package snyk.common.lsp

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalListener
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.isSnykOSSLSEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.SnykFile
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressEnd
import org.eclipse.lsp4j.WorkDoneProgressKind.begin
import org.eclipse.lsp4j.WorkDoneProgressKind.end
import org.eclipse.lsp4j.WorkDoneProgressKind.report
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import org.jetbrains.kotlin.idea.util.application.executeOnPooledThread
import snyk.common.ProductType
import snyk.common.SnykFileIssueComparator
import snyk.trust.WorkspaceTrustService
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class SnykLanguageClient() : LanguageClient {
    private val progressLock: ReentrantLock = ReentrantLock()

    // TODO FIX Log Level
    val logger = Logger.getInstance("Snyk Language Server").also { it.setLevel(LogLevel.DEBUG) }

    private val progresses: Cache<String, ProgressIndicator> =
        Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.SECONDS)
            .removalListener(
                RemovalListener<String, ProgressIndicator> { _, indicator, _ ->
                    indicator?.cancel()
                }
            )
            .build()
    private val progressReportMsgCache: Cache<String, MutableList<WorkDoneProgressReport>> =
        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build()
    private val progressEndMsgCache: Cache<String, WorkDoneProgressEnd> =
        Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).build()

    override fun telemetryEvent(`object`: Any?) {
        // do nothing
    }

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        // do nothing for now
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams?): CompletableFuture<ApplyWorkspaceEditResponse> {
        val project = params?.edit?.changes?.keys
            ?.firstNotNullOfOrNull {
                ProjectLocator.getInstance().guessProjectForFile(it.toVirtualFile())
            }
            ?: ProjectUtil.getActiveProject()
            ?: return CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(false))

        WriteCommandAction.runWriteCommandAction(project) {
            params?.edit?.changes?.forEach {
                DocumentChanger.applyChange(it)
            }
        }

        refreshUI()
        return CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(true))
    }

    override fun refreshCodeLenses(): CompletableFuture<Void> {
        return refreshUI()
    }

    override fun refreshInlineValues(): CompletableFuture<Void> {
        return refreshUI()
    }

    private fun refreshUI(): CompletableFuture<Void> {
        val completedFuture: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
        if (!isSnykCodeLSEnabled()) return completedFuture
        ProjectUtil.getOpenProjects().forEach { project ->
            ReadAction.run<RuntimeException> {
                refreshAnnotationsForOpenFiles(project)
            }
        }
        VirtualFileManager.getInstance().asyncRefresh()
        return completedFuture
    }

    @JsonNotification(value = "$/snyk.scan")
    fun snykScan(snykScan: SnykScanParams) {
        if (snykScan.product == "code" && !isSnykCodeLSEnabled()) {
            return
        }
        if (snykScan.product == "oss" && !isSnykOSSLSEnabled()) {
            return
        }
        try {
            getScanPublishersFor(snykScan).forEach { (project, scanPublisher) ->
                processSnykScan(snykScan, scanPublisher, project)
            }
        } catch (e: Exception) {
            logger.error("Error processing snyk scan", e)
        }
    }

    private fun processSnykScan(snykScan: SnykScanParams, scanPublisher: SnykScanListenerLS, project: Project) {
        val product = when (snykScan.product) {
            "code" -> ProductType.CODE_SECURITY
            "oss" -> ProductType.OSS
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
                processSuccessfulScan(snykScan, scanPublisher, project)
            }

            "error" -> {
                ScanState.scanInProgress[key] = false
                scanPublisher.scanningError(snykScan)
            }
        }
    }

    private fun processSuccessfulScan(snykScan: SnykScanParams, scanPublisher: SnykScanListenerLS, project: Project) {
        logger.info("Scan completed")
        when (snykScan.product) {
            "oss" -> {
                scanPublisher.scanningOssFinished(getSnykResult(project, snykScan))
            }

            "code" -> {
                scanPublisher.scanningSnykCodeFinished(getSnykResult(project, snykScan))
            }

            "iac" -> {
                // TODO implement
            }
        }
    }

    /**
     * Get all the scan publishers for the given scan. As the folder path could apply to different projects
     * containing that content root, we need to notify all of them.
     */
    private fun getScanPublishersFor(snykScan: SnykScanParams): Set<Pair<Project, SnykScanListenerLS>> {
        return getProjectsForFolderPath(snykScan.folderPath)
            .mapNotNull { p ->
                getSyncPublisher(p, SnykScanListenerLS.SNYK_SCAN_TOPIC)?.let { scanListenerLS ->
                    Pair(p, scanListenerLS)
                }
            }.toSet()
    }

    private fun getProjectsForFolderPath(folderPath: String) =
        getOpenedProjects().filter {
            it.getContentRootVirtualFiles()
                .contains(folderPath.toVirtualFile())
        }

    @Suppress("UselessCallOnNotNull") // because lsp4j doesn't care about Kotlin non-null safety
    private fun getSnykResult(project: Project, snykScan: SnykScanParams): Map<SnykFile, List<ScanIssue>> {
        check(snykScan.product == "code" || snykScan.product == "oss" ) { "Expected Snyk Code or Snyk OSS scan result" }
        if (snykScan.issues.isNullOrEmpty()) return emptyMap()

        val pluginSettings = pluginSettings()
        val includeIgnoredIssues = pluginSettings.ignoredIssuesEnabled
        val includeOpenedIssues = pluginSettings.openIssuesEnabled

        val processedIssues = if (pluginSettings.isGlobalIgnoresFeatureEnabled) { //
            snykScan.issues.filter { it.isVisible(includeOpenedIssues, includeIgnoredIssues) }
        } else {
            snykScan.issues
        }

        val map = processedIssues
            .groupBy { it.filePath }
            .mapNotNull { (file, issues) -> SnykFile(project, file.toVirtualFile()) to issues.sorted() }
            .map {
                // initialize all calculated values before they are needed, so we don't have to do it in the UI thread
                it.first.relativePath
                it.second.forEach { i -> i.textRange }
                it
            }
            .filter { it.second.isNotEmpty() }
            .toMap()
        return map.toSortedMap(SnykFileIssueComparator(map))
    }

    @JsonNotification(value = "$/snyk.hasAuthenticated")
    fun hasAuthenticated(param: HasAuthenticatedParam) {
        pluginSettings().token = param.token

        ProjectUtil.getOpenProjects().forEach {
            LanguageServerWrapper.getInstance().sendScanCommand(it)
        }

        if (!param.token.isNullOrBlank()) {
            SnykBalloonNotificationHelper.showInfo("Authentication successful", ProjectUtil.getActiveProject()!!)
        }
    }

    @JsonNotification(value = "$/snyk.addTrustedFolders")
    fun addTrustedPaths(param: SnykTrustedFoldersParams) {
        val trustService = service<WorkspaceTrustService>()
        param.trustedFolders.forEach { it.toNioPathOrNull()?.let { path -> trustService.addTrustedPath(path) } }
    }


    override fun createProgress(params: WorkDoneProgressCreateParams?): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    private fun createProgressInternal(token: String, begin: WorkDoneProgressBegin) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(ProjectUtil.getActiveProject(), "Snyk: ${begin.title}", true) {
            override fun run(indicator: ProgressIndicator) {
                logger.debug("###### Creating progress indicator for: $token, title: ${begin.title}, message: ${begin.message}")
                indicator.isIndeterminate = false
                indicator.text = begin.title
                indicator.text2 = begin.message
                indicator.fraction = 0.1
                progresses.put(token, indicator)
                while (!indicator.isCanceled) {
                    Thread.sleep(1000)
                }
                logger.debug("###### Progress indicator canceled for token: $token")
            }
        })
    }

    override fun notifyProgress(params: ProgressParams) {
        // first: check if progress has begun
        val token = params.token?.left ?: return
        if (progresses.getIfPresent(token) != null) {
            processProgress(params)
        } else {
            when (val progressNotification = params.value.left) {
                is WorkDoneProgressEnd -> {
                    progressEndMsgCache.put(token, progressNotification)
                }

                is WorkDoneProgressReport -> {
                    val list = progressReportMsgCache.get(token) { mutableListOf() }
                    list.add(progressNotification)
                }

                else -> {
                    processProgress(params)
                }
            }
            return
        }
    }

    private fun processProgress(params: ProgressParams?) {
        progressLock.lock()
        try {
            val token = params?.token?.left ?: return
            val workDoneProgressNotification = params.value.left ?: return
            when (workDoneProgressNotification.kind) {
                begin -> {
                    val begin: WorkDoneProgressBegin = workDoneProgressNotification as WorkDoneProgressBegin
                    createProgressInternal(token, begin)
                    // wait until the progress indicator is created in the background thread
                    while (progresses.getIfPresent(token) == null) {
                        Thread.sleep(100)
                    }

                    // process previously reported progress and end messages for token
                    processCachedProgressReports(token)
                    processCachedEndReport(token)
                }

                report -> {
                    progressReport(token, workDoneProgressNotification)
                }

                end -> {
                    progressEnd(token, workDoneProgressNotification)
                }

                null -> {}
            }
        } finally {
            progressLock.unlock()
        }
    }

    private fun processCachedEndReport(token: String) {
        val endReport = progressEndMsgCache.getIfPresent(token)
        if (endReport != null) {
            progressEnd(token, endReport)
        }
        progressEndMsgCache.invalidate(token)
    }

    private fun processCachedProgressReports(token: String) {
        val reportParams = progressReportMsgCache.getIfPresent(token)
        if (reportParams != null) {
            reportParams.forEach { report ->
                progressReport(token, report)
            }
            progressReportMsgCache.invalidate(token)
        }
    }

    private fun progressReport(token: String, workDoneProgressNotification: WorkDoneProgressNotification) {
        logger.debug("###### Received progress report notification for token: $token")
        val indicator = progresses.getIfPresent(token)!!
        val report: WorkDoneProgressReport = workDoneProgressNotification as WorkDoneProgressReport
        logger.debug("###### Token: $token, progress: ${report.percentage}%, message: ${report.message}")

        indicator.text = report.message
        indicator.isIndeterminate = false
        indicator.fraction = report.percentage / 100.0
        return
    }

    private fun progressEnd(token: String, workDoneProgressNotification: WorkDoneProgressNotification) {
        logger.debug("###### Received progress end notification for token: $token")
        val indicator = progresses.getIfPresent(token)!!
        val workDoneProgressEnd = workDoneProgressNotification as WorkDoneProgressEnd
        indicator.text = workDoneProgressEnd.message
        progresses.invalidate(token)
        return
    }

    override fun logTrace(params: LogTraceParams?) {
        logger.info(params?.message)
    }

    override fun showMessage(messageParams: MessageParams?) {
        val project = ProjectUtil.getActiveProject()
        if (project == null) {
            logger.info(messageParams?.message)
            return
        }
        when (messageParams?.type) {
            MessageType.Error -> SnykBalloonNotificationHelper.showError(messageParams.message, project)
            MessageType.Warning -> SnykBalloonNotificationHelper.showWarn(messageParams.message, project)
            MessageType.Info -> {
                val notification = SnykBalloonNotificationHelper.showInfo(messageParams.message, project)
                executeOnPooledThread {
                    Thread.sleep(5000)
                    notification.expire()
                }
            }

            MessageType.Log -> logger.info(messageParams.message)
            null -> {}
        }
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        val project = ProjectUtil.getActiveProject() ?: return CompletableFuture.completedFuture(MessageActionItem(""))
        showMessageRequestFutures.clear()
        val actions = requestParams.actions.map {
            object : AnAction(it.title) {
                override fun actionPerformed(p0: AnActionEvent) {
                    showMessageRequestFutures.put(MessageActionItem(it.title))
                }
            }
        }.toSet().toTypedArray()

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

    companion object {
        // we only allow one message request at a time
        val showMessageRequestFutures = ArrayBlockingQueue<MessageActionItem>(1)
    }
}
