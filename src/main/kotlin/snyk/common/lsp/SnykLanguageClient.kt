package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.queryParameters
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.events.SnykScanSummaryListenerLS
import io.snyk.plugin.events.SnykShowIssueDetailListener
import io.snyk.plugin.events.SnykShowIssueDetailListener.Companion.SHOW_DETAIL_ACTION
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.getDecodedParam
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.sha256
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.jetbrains.concurrency.runAsync
import snyk.common.ProductType
import snyk.common.editor.DocumentChanger
import snyk.common.lsp.progress.ProgressManager
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.sdk.SdkHelper
import snyk.trust.WorkspaceTrustService
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Processes Language Server requests and notifications from the server to the IDE
 */
class SnykLanguageClient :
    LanguageClient,
    Disposable {
    val logger = Logger.getInstance("Snyk Language Server")
    val gson = Gson()
    val progressManager = ProgressManager.getInstance()

    private var disposed = false
        get() {
            return ApplicationManager.getApplication().isDisposed || field
        }

    fun isDisposed() = disposed

    override fun telemetryEvent(`object`: Any?) {
        // do nothing
    }

    override fun notifyProgress(params: ProgressParams) {
        progressManager.notifyProgress(params)
    }

    override fun publishDiagnostics(diagnosticsParams: PublishDiagnosticsParams?) {
        if (diagnosticsParams == null) {
            return
        }

        val filePath = diagnosticsParams.uri

        try {
            val path = Paths.get(URI.create(filePath)).toString()
            getScanPublishersFor(path).forEach { (project, scanPublisher) ->
                updateCache(project, filePath, diagnosticsParams, scanPublisher)
            }
        } catch (e: Exception) {
            logger.error("Error publishing the new diagnostics", e)
        }
    }

    private fun updateCache(
        project: Project,
        filePath: String,
        diagnosticsParams: PublishDiagnosticsParams,
        scanPublisher: SnykScanListenerLS
    ) {
        if (disposed) return
        val snykFile = SnykFile(project, filePath.toVirtualFile())
        val firstDiagnostic = diagnosticsParams.diagnostics.firstOrNull()
        val product = firstDiagnostic?.source

        //If the diagnostics for the file is empty, clear the cache.
        if (firstDiagnostic == null) {
            scanPublisher.onPublishDiagnostics(LsProduct.Code, snykFile, emptyList())
            scanPublisher.onPublishDiagnostics(LsProduct.OpenSource, snykFile, emptyList())
            scanPublisher.onPublishDiagnostics(LsProduct.InfrastructureAsCode, snykFile, emptyList())
            return
        }

        val issueList = getScanIssues(diagnosticsParams)
        if (product != null) {
            scanPublisher.onPublishDiagnostics(LsProduct.getFor(product), snykFile, issueList)
        }

        return
    }

    fun getScanIssues(diagnosticsParams: PublishDiagnosticsParams): List<ScanIssue> {
        val issueList = diagnosticsParams.diagnostics.stream().map {
            val issue = Gson().fromJson(it.data.toString(), ScanIssue::class.java)
            // load textRange for issue so it doesn't happen in UI thread
            issue.textRange
            if (issue.isIgnored() && !pluginSettings().isGlobalIgnoresFeatureEnabled) {
                // apparently the server has consistent ignores activated
                pluginSettings().isGlobalIgnoresFeatureEnabled = true
            }
            issue
        }.toList()

        return issueList
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams?): CompletableFuture<ApplyWorkspaceEditResponse> {
        val falseFuture = CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(false))
        if (disposed) return falseFuture
        val project =
            params
                ?.edit
                ?.changes
                ?.keys
                ?.firstNotNullOfOrNull {
                    ProjectLocator.getInstance().guessProjectForFile(it.toVirtualFile())
                }
                ?: ProjectUtil.getActiveProject()
                ?: return falseFuture

        WriteCommandAction.runWriteCommandAction(project) {
            params?.edit?.changes?.forEach {
                DocumentChanger.applyChange(it)
            }
        }

        refreshUI()
        return CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(true))
    }

    override fun refreshCodeLenses(): CompletableFuture<Void> = refreshUI()

    override fun refreshInlineValues(): CompletableFuture<Void> = refreshUI()

    private fun refreshUI(): CompletableFuture<Void> {
        val completedFuture: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        if (disposed) return completedFuture
        runAsync {
            ProjectManager
                .getInstance()
                .openProjects
                .filter { !it.isDisposed }
                .forEach { project ->
                    ReadAction.run<RuntimeException> {
                        if (!project.isDisposed) refreshAnnotationsForOpenFiles(project)
                    }
                }
        }
        return completedFuture
    }

    @JsonNotification(value = "$/snyk.folderConfigs")
    fun folderConfig(folderConfigParam: FolderConfigsParam?) {
        val folderConfigs = folderConfigParam?.folderConfigs ?: emptyList()
        runAsync {
            service<FolderConfigSettings>().addAll(folderConfigs)
        }
    }

    @JsonNotification(value = "$/snyk.scan")
    fun snykScan(snykScan: SnykScanParams) {
        if (disposed) return
        try {
            getScanPublishersFor(snykScan.folderPath).forEach { (_, scanPublisher) ->
                processSnykScan(snykScan, scanPublisher)
            }
        } catch (e: Exception) {
            logger.error("Error processing snyk scan", e)
        }
    }

    private fun processSnykScan(
        snykScan: SnykScanParams,
        scanPublisher: SnykScanListenerLS,
    ) {
        val product =
            when (LsProduct.getFor(snykScan.product)) {
                LsProduct.Code -> ProductType.CODE_SECURITY
                LsProduct.OpenSource-> ProductType.OSS
                LsProduct.InfrastructureAsCode-> ProductType.IAC
                else -> return
            }
        val key = ScanInProgressKey(snykScan.folderPath.toVirtualFile(), product)
        when (snykScan.status) {
            LsScanState.InProgress.value-> {
                if (ScanState.scanInProgress[key] == true) return
                ScanState.scanInProgress[key] = true
                scanPublisher.scanningStarted(snykScan)
            }

            LsScanState.Success.value -> {
                ScanState.scanInProgress[key] = false
                processSuccessfulScan(snykScan, scanPublisher)
            }

            LsScanState.Error.value -> {
                ScanState.scanInProgress[key] = false
                scanPublisher.scanningError(snykScan)
            }
        }
    }

    private fun processSuccessfulScan(
        snykScan: SnykScanParams,
        scanPublisher: SnykScanListenerLS,
    ) {
        logger.info("Scan completed")

        when (LsProduct.getFor(snykScan.product)) {
            LsProduct.OpenSource -> scanPublisher.scanningOssFinished()
            LsProduct.Code -> scanPublisher.scanningSnykCodeFinished()
            LsProduct.InfrastructureAsCode -> scanPublisher.scanningIacFinished()
            LsProduct.Container -> Unit
            LsProduct.Unknown -> Unit
        }
    }

    /**
     * Get all the scan publishers for the given scan. As the folder path could apply to different projects
     * containing that content root, we need to notify all of them.
     */
    private fun getScanPublishersFor(path: String): Set<Pair<Project, SnykScanListenerLS>> =
        getProjectsForFolderPath(path)
            .mapNotNull { p ->
                getSyncPublisher(p, SnykScanListenerLS.SNYK_SCAN_TOPIC)?.let { scanListenerLS ->
                    Pair(p, scanListenerLS)
                }
            }.toSet()

    private fun getProjectsForFolderPath(folderPath: String) =
        ProjectManager.getInstance().openProjects.filter {
            it
                .getContentRootVirtualFiles().any { ancestor ->
                    val folder = folderPath.toVirtualFile()
                    VfsUtilCore.isAncestor(ancestor, folder, true) || ancestor == folder
                }
        }

    @JsonNotification(value = "$/snyk.scanSummary")
    fun snykScanSummary(summaryParams: SnykScanSummaryParams) {
        if (disposed) return
        ProjectManager.getInstance().openProjects.filter{!it.isDisposed}.forEach { p ->
            logger.debug("Publishing Snyk scan summary for $p: ${summaryParams.scanSummary}")
            getSyncPublisher(p, SnykScanSummaryListenerLS.SNYK_SCAN_SUMMARY_TOPIC)?.onSummaryReceived(summaryParams)
        }
    }

    @JsonNotification(value = "$/snyk.hasAuthenticated")
    fun hasAuthenticated(param: HasAuthenticatedParam) {
        if (disposed) return
        val oldToken = pluginSettings().token ?: ""
        val oldApiUrl = pluginSettings().customEndpointUrl
        if (oldToken == param.token && oldApiUrl == param.apiUrl) return

        if (!param.apiUrl.isNullOrBlank()) {
            pluginSettings().customEndpointUrl = param.apiUrl
        }

        logger.info("received authentication information: Token-Length: ${param.token?.length}}, URL: ${param.apiUrl}")
        logger.info("use token-auth? ${pluginSettings().useTokenAuthentication}")
        logger.debug("is same token?  ${oldToken == param.token}")
        logger.debug("old-token-hash: ${oldToken.sha256()}, new-token-hash: ${param.token?.sha256()}" )

        pluginSettings().token = param.token

        // we use internal API here, as we need to force immediate persistence to ensure new
        // refresh tokens are always persisted, not only every 5 min.
        StoreUtil.saveSettings(ApplicationManager.getApplication(), true)
        logger.info("force-saved settings")

        if (oldToken.isBlank() && !param.token.isNullOrBlank() && pluginSettings().scanOnSave) {
            val wrapper = LanguageServerWrapper.getInstance()
            ProjectManager.getInstance().openProjects.forEach {
                wrapper.sendScanCommand(it)
            }
        }
    }

    @JsonRequest(value = "workspace/snyk.sdks")
    fun getSdks(workspaceFolder: WorkspaceFolder): CompletableFuture<List<LsSdk>> {
        val project =
            guessProjectForFile(workspaceFolder.uri.toVirtualFile()) ?: return CompletableFuture.completedFuture(
                emptyList()
            )
        return CompletableFuture.completedFuture(SdkHelper.getSdks(project))
    }

    @JsonNotification(value = "$/snyk.addTrustedFolders")
    fun addTrustedPaths(param: SnykTrustedFoldersParams) {
        if (disposed) return
        val trustService = service<WorkspaceTrustService>()
        param.trustedFolders.forEach { it.toNioPathOrNull()?.let { path -> trustService.addTrustedPath(path) } }
    }

    override fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void> {
        return progressManager.createProgress(params)
    }

    override fun logTrace(params: LogTraceParams?) {
        if (disposed) return
        logger.info(params?.message)
    }

    override fun showMessage(messageParams: MessageParams?) {
        if (disposed) return
        val project = ProjectUtil.getActiveProject()
        when (messageParams?.type) {
            MessageType.Error -> {
                val m = cutMessage(messageParams)
                SnykBalloonNotificationHelper.showError(m, project)
            }

            MessageType.Warning -> {
                val m = cutMessage(messageParams)
                SnykBalloonNotificationHelper.showWarn(m, project)
            }

            MessageType.Info -> {
                val notification = SnykBalloonNotificationHelper.showInfo(messageParams.message, project)
                ApplicationManager.getApplication().executeOnPooledThread {
                    Thread.sleep(30000)
                    notification.expire()
                }
            }

            MessageType.Log -> logger.info(messageParams.message)
            null -> {}
        }
    }

    private fun cutMessage(messageParams: MessageParams): String {
        return if (messageParams.message.length > 500) {
            messageParams.message.substring(0, 500) + "..."
        } else {
            messageParams.message
        }
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        val completedFuture = CompletableFuture.completedFuture(MessageActionItem(""))
        if (disposed) return completedFuture
        val project = ProjectUtil.getActiveProject() ?: return completedFuture

        showMessageRequestFutures.clear()
        val actions =
            requestParams.actions
                .map {
                    object : AnAction(it.title) {
                        override fun actionPerformed(p0: AnActionEvent) {
                            showMessageRequestFutures.put(MessageActionItem(it.title))
                        }
                    }
                }.toSet()
                .toTypedArray()

        val notification = SnykBalloonNotificationHelper.showInfo(requestParams.message, project, *actions)
        val messageActionItem = showMessageRequestFutures.poll(10, TimeUnit.SECONDS)
        notification.expire()
        return CompletableFuture.completedFuture(messageActionItem ?: MessageActionItem(""))
    }

    override fun logMessage(message: MessageParams?) {
        message?.let {
            when (it.type) {
                MessageType.Error -> logger.error(it.message)
                MessageType.Warning -> logger.warn(it.message)
                MessageType.Info -> logger.info(it.message)
                MessageType.Log -> logger.debug(it.message)
                null -> logger.info(it.message)
            }
        }
    }

    /**
     * We don't need this custom notification, as LSP4j already supports LSP 3.17.
     * This custom notification is sent from the server to give clients that only support
     * lower LSP protocol versions the chance to retrieve the <pre>data</pre> field of
     * the diagnostic that contains the issue detail data by implementing a custom
     * notification listener (e.g. Visual Studio)
     */
    @JsonNotification("\$/snyk.publishDiagnostics316")
    fun publishDiagnostics316(ignored: PublishDiagnosticsParams?) = Unit

    companion object {
        // we only allow one message request at a time
        val showMessageRequestFutures = ArrayBlockingQueue<MessageActionItem>(1)
    }

    override fun dispose() {
        disposed = true
    }

    init {
        Disposer.register(SnykPluginDisposable.getInstance(), this)
    }

    /**
     * Intercept window/showDocument messages from LS so that we can handle AI fix actions within the IDE.
     */
    override fun showDocument(param: ShowDocumentParams): CompletableFuture<ShowDocumentResult> {
        if (disposed) return CompletableFuture.completedFuture(ShowDocumentResult(false))

        val uri = URI.create(param.uri)

        return if (
            uri.scheme == "snyk" &&
            uri.getDecodedParam("product") == LsProduct.Code.longName &&
            uri.getDecodedParam("action") == SHOW_DETAIL_ACTION
            ) {

            // Track whether we have successfully sent any notifications
            var success = false

            uri.queryParameters["issueId"]?.let { issueId ->
                ProjectManager.getInstance().openProjects.filter{!it.isDisposed}.forEach { project ->
                    val aiFixParams = AiFixParams(issueId, ProductType.CODE_SECURITY)
                    logger.debug("Publishing Snyk AI Fix notification for issue $issueId.")
                    getSyncPublisher(project, SnykShowIssueDetailListener.SHOW_ISSUE_DETAIL_TOPIC)?.onShowIssueDetail(aiFixParams)
                    success = true
                }
            } ?: run { logger.info("Received showDocument URI with no issueID: $uri") }
            CompletableFuture.completedFuture(ShowDocumentResult(success))
        } else {
            logger.debug("URI does not match Snyk scheme - passing to default handler: ${param.uri}")
            super.showDocument(param)
        }
    }
}
