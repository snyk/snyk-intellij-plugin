package snyk.common.lsp

import com.google.gson.Gson
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VfsUtilCore
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
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
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import org.jetbrains.concurrency.runAsync
import snyk.common.ProductType
import snyk.common.editor.DocumentChanger
import snyk.common.lsp.progress.ProgressManager
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.trust.WorkspaceTrustService
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
    val progressManager = ProgressManager()

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
            getScanPublishersFor(filePath.toVirtualFile().path).forEach { (project, scanPublisher) ->
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
        val snykFile = SnykFile(project, filePath.toVirtualFile())
        val firstDiagnostic = diagnosticsParams.diagnostics.firstOrNull()
        val product = firstDiagnostic?.source

        //If the diagnostics for the file is empty, clear the cache.
        if (firstDiagnostic == null) {
            scanPublisher.onPublishDiagnostics("code", snykFile, emptyList())
            scanPublisher.onPublishDiagnostics("oss", snykFile, emptyList())
            scanPublisher.onPublishDiagnostics("iac", snykFile, emptyList())
            return
        }

        val issueList = getScanIssues(diagnosticsParams)
        if (product != null) {
            scanPublisher.onPublishDiagnostics(product, snykFile, issueList)
        }

        return
    }

    fun getScanIssues(diagnosticsParams: PublishDiagnosticsParams): List<ScanIssue> {
        val issueList = diagnosticsParams.diagnostics.stream().map {
            val issue = Gson().fromJson(it.data.toString(), ScanIssue::class.java)
            // load textrange for issue so it doesn't happen in UI thread
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
            when (snykScan.product) {
                "code" -> ProductType.CODE_SECURITY
                "oss" -> ProductType.OSS
                "iac" -> ProductType.IAC
                else -> return
            }
        val key = ScanInProgressKey(snykScan.folderPath.toVirtualFile(), product)
        when (snykScan.status) {
            "inProgress" -> {
                if (ScanState.scanInProgress[key] == true) return
                ScanState.scanInProgress[key] = true
                scanPublisher.scanningStarted(snykScan)
            }

            "success" -> {
                ScanState.scanInProgress[key] = false
                processSuccessfulScan(snykScan, scanPublisher)
            }

            "error" -> {
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

        when (snykScan.product) {
            "oss" -> scanPublisher.scanningOssFinished()
            "code" -> {
                LanguageServerWrapper.getInstance().refreshFeatureFlags()
                scanPublisher.scanningSnykCodeFinished()
            }
            "iac" -> scanPublisher.scanningIacFinished()
            "container" -> TODO()
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

    @JsonNotification(value = "$/snyk.hasAuthenticated")
    fun hasAuthenticated(param: HasAuthenticatedParam) {
        if (disposed) return
        if (pluginSettings().token == param.token) return
        pluginSettings().token = param.token
        ApplicationManager.getApplication().saveSettings()

        if (pluginSettings().token?.isNotEmpty() == true && pluginSettings().scanOnSave) {
            val wrapper = LanguageServerWrapper.getInstance()
            ProjectManager.getInstance().openProjects.forEach {
                wrapper.sendScanCommand(it)
            }
        }
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
                SnykBalloonNotificationHelper.showError(messageParams.message, project)
            }

            MessageType.Warning -> SnykBalloonNotificationHelper.showWarn(messageParams.message, project)
            MessageType.Info -> {
                val notification = SnykBalloonNotificationHelper.showInfo(messageParams.message, project)
                ApplicationManager.getApplication().executeOnPooledThread {
                    Thread.sleep(10000)
                    notification.expire()
                }
            }

            MessageType.Log -> logger.info(messageParams.message)
            null -> {}
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
}
