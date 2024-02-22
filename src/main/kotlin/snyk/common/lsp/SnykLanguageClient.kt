package snyk.common.lsp

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.util.io.toNioPathOrNull
import io.snyk.plugin.events.SnykCodeScanListenerLS
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.SnykCodeFile
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
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import snyk.common.SnykCodeFileIssueComparator
import snyk.trust.WorkspaceTrustService
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture

class SnykLanguageClient : LanguageClient {
    // TODO FIX Log Level
    val logger = Logger.getInstance("Snyk Language Server").also { it.setLevel(LogLevel.DEBUG) }
    val progresses: MutableMap<String, ProgressIndicator> =
        Collections.synchronizedMap(HashMap<String, ProgressIndicator>())

    override fun telemetryEvent(`object`: Any?) {
        // do nothing
    }

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        // do nothing for now
    }

    override fun applyEdit(params: ApplyWorkspaceEditParams?): CompletableFuture<ApplyWorkspaceEditResponse> {
        val project = ProjectUtil.getActiveProject() ?: return CompletableFuture.completedFuture(
            ApplyWorkspaceEditResponse(false)
        )

        WriteCommandAction.runWriteCommandAction(project) {
            params?.edit?.changes?.forEach {
                DocumentChanger.applyChange(it)
            }
        }

        DaemonCodeAnalyzer.getInstance(project).restart()
        return CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(true))
    }

    override fun refreshCodeLenses(): CompletableFuture<Void> {
        val activeProject = ProjectUtil.getActiveProject() ?: return CompletableFuture.completedFuture(null)
        DaemonCodeAnalyzer.getInstance(activeProject).restart()
        return CompletableFuture.completedFuture(null)
    }

    override fun refreshInlineValues(): CompletableFuture<Void> {
        val activeProject = ProjectUtil.getActiveProject() ?: return CompletableFuture.completedFuture(null)
        DaemonCodeAnalyzer.getInstance(activeProject).restart()
        return CompletableFuture.completedFuture(null)
    }

    @JsonNotification(value = "$/snyk.scan")
    fun snykScan(snykScan: SnykScanParams) {
        if (snykScan.product != ScanState.SNYK_CODE || !isSnykCodeLSEnabled()) return
        try {
            getScanPublishersFor(snykScan).forEach { (project, scanPublisher) ->
                when (snykScan.status) {
                    "inProgress" -> {
                        if (ScanState.scanInProgress[snykScan.product] == true) return
                        ScanState.scanInProgress[snykScan.product] = true
                        scanPublisher.scanningStarted(snykScan)
                    }

                    "success" -> {
                        ScanState.scanInProgress[snykScan.product] = false
                        processSuccessfulScan(snykScan, scanPublisher, project)
                    }

                    "error" -> {
                        ScanState.scanInProgress[snykScan.product] = false
                        scanPublisher.scanningSnykCodeError(snykScan)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing snyk scan", e)
        }
    }

    private fun processSuccessfulScan(
        snykScan: SnykScanParams,
        scanPublisher: SnykCodeScanListenerLS,
        project: Project
    ) {
        logger.info("Scan completed")
        when (snykScan.product) {
            "oss" -> {
                // TODO implement
            }

            "code" -> {
                scanPublisher.scanningSnykCodeFinished(getSnykCodeResult(project, snykScan))
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
    private fun getScanPublishersFor(snykScan: SnykScanParams): Set<Pair<Project, SnykCodeScanListenerLS>> {
        return getOpenedProjects()
            .filter {
                it.getContentRootVirtualFiles().map { virtualFile -> virtualFile.path }.contains(snykScan.folderPath)
            }
            .mapNotNull { p ->
                getSyncPublisher(p, SnykCodeScanListenerLS.SNYK_SCAN_TOPIC)?.let { Pair(p, it) }
            }.toSet()
    }

    private fun getSnykCodeResult(project: Project, snykScan: SnykScanParams): Map<SnykCodeFile, List<ScanIssue>> {
        check(snykScan.product == "code") { "Expected Snyk Code scan result" }
        val map = snykScan.issues
            .groupBy { it.filePath }
            .map { (file, issues) -> SnykCodeFile(project, file.toVirtualFile()) to issues.sorted() }
            .filter { it.second.isNotEmpty() }
            .toMap()
        return map.toSortedMap(SnykCodeFileIssueComparator(map))
    }

    override fun createProgress(params: WorkDoneProgressCreateParams?): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    private fun createProgressInternal(token: String, begin: WorkDoneProgressBegin) {
        ProgressManager.getInstance()
            .run(object : Task.Backgroundable(ProjectUtil.getActiveProject(), "Snyk: ${begin.title}", true) {
                override fun run(indicator: ProgressIndicator) {
                    progresses[token] = indicator
                    while (!indicator.isCanceled) {
                        Thread.sleep(100)
                    }
                    progresses.remove(token)
                }
            })
    }

    @JsonNotification(value = "$/snyk.hasAuthenticated")
    fun hasAuthenticated(param: HasAuthenticatedParam) {
        pluginSettings().token = param.token
        LanguageServerWrapper.getInstance().sendScanCommand()

        if (!param.token.isNullOrBlank()) {
            SnykBalloonNotificationHelper.showInfo("Authentication successful", ProjectUtil.getActiveProject()!!)
        }
    }

    @JsonNotification(value = "$/snyk.addTrustedFolders")
    fun addTrustedPaths(param: SnykTrustedFoldersParams) {
        val trustService = service<WorkspaceTrustService>()
        param.trustedFolders.forEach { it.toNioPathOrNull()?.let { path -> trustService.addTrustedPath(path) } }
    }

    override fun notifyProgress(params: ProgressParams?) {
        val token = params?.token?.left ?: return
        val workDoneProgressNotification = params.value.left ?: return
        when (workDoneProgressNotification.kind) {
            begin -> {
                val begin: WorkDoneProgressBegin = workDoneProgressNotification as WorkDoneProgressBegin
                createProgressInternal(token, begin)
                val indicator = progresses[token] ?: return
                indicator.isIndeterminate = false
                indicator.text = begin.title
                indicator.text2 = begin.message
                indicator.fraction = begin.percentage / 100.0
            }

            report -> {
                while (progresses[token] == null) {
                    Thread.sleep(1000)
                }
                val indicator = progresses[token] ?: return
                val report: WorkDoneProgressReport = workDoneProgressNotification as WorkDoneProgressReport
                indicator.text = report.message
                indicator.isIndeterminate = false
                indicator.fraction = report.percentage / 100.0
                if (report.percentage == 100) {
                    indicator.cancel()
                }
            }

            end -> {
                while (progresses[token] == null) {
                    Thread.sleep(1000)
                }
                val indicator = progresses[token] ?: return
                val workDoneProgressEnd = workDoneProgressNotification as WorkDoneProgressEnd
                indicator.text = workDoneProgressEnd.message
                indicator.cancel()
            }

            null -> {}
        }
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
            MessageType.Info -> SnykBalloonNotificationHelper.showInfo(messageParams.message, project)
            MessageType.Log -> logger.info(messageParams.message)
            null -> {}
        }
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        val project = ProjectUtil.getActiveProject() ?: return CompletableFuture.completedFuture(MessageActionItem(""))
        val actions = requestParams.actions
            .map {
                object : AnAction(it.title) {
                    override fun actionPerformed(p0: AnActionEvent) {
                        val future = CompletableFuture.completedFuture(MessageActionItem(it.title))
                        showMessageRequestFutures.put(future)
                    }
                }
            }.toSet().toTypedArray()

        val notification = SnykBalloonNotificationHelper.showInfo(requestParams.message, project, *actions)
        val future = showMessageRequestFutures.take()
        notification.hideBalloon()
        return future
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
        val showMessageRequestFutures = ArrayBlockingQueue<CompletableFuture<MessageActionItem>>(1)
    }
}
