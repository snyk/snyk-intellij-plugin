package snyk.common.lsp

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
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
import java.util.concurrent.CompletableFuture

class SnykLanguageClient : LanguageClient {
    val logger = Logger.getInstance("Snyk Language Server")

    override fun telemetryEvent(`object`: Any?) {
        // do nothing
    }

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        // do nothing for now
    }

    @JsonNotification(value = "$/snyk.scan")
    fun snykScan(snykScan: SnykScanParams) {
        // populate SnykResultCache
        logger.info("snyk.scan notification received")
        // TODO: feature flag!

        getScanPublishersFor(snykScan).forEach { (project, scanPublisher) ->
            when (snykScan.status) {
                "inProgress" -> {}
                "success" -> {
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
            }
        }
    }

    /**
     * Get all the scan publishers for the given scan. As the folder path could apply to different projects
     * containing that content root, we need to notify all of them.
     */
    private fun getScanPublishersFor(snykScan: SnykScanParams): Set<Pair<Project, SnykScanListener>> {
        return getOpenedProjects()
            .filter {
                it.getContentRootVirtualFiles().map { virtualFile -> virtualFile.url }.contains(snykScan.folderPath)
            }
            .mapNotNull { p -> getSyncPublisher(p, SnykScanListener.SNYK_SCAN_TOPIC)?.let { Pair(p, it) } }.toSet()
    }

    private fun getSnykCodeResult(project: Project, snykScan: SnykScanParams): Map<SnykCodeFile, List<ScanIssue>> {
        check(snykScan.product == "code") { "Expected Snyk Code scan result" }
        return snykScan.issues
            .groupBy { it.virtualFile }
            .map { (file, issues) -> SnykCodeFile(project, file!!) to issues.sorted() }
            .filter { it.second.isNotEmpty() }
            .toMap()
            .toSortedMap { o1, o2 -> o1.virtualFile.path.compareTo(o2.virtualFile.path) }
    }

    override fun createProgress(params: WorkDoneProgressCreateParams?): CompletableFuture<Void> {
        // TODO implement
        return CompletableFuture.completedFuture(null)
    }

    override fun notifyProgress(params: ProgressParams?) {
        // TODO implement
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

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
        // FIXME: implement with dialog
        return CompletableFuture.completedFuture(MessageActionItem("OK"))
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
}
