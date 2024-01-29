package snyk.common.lsp

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import ai.deepcode.javaclient.responses.ExampleCommitFix
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.StandardFileSystems
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.eclipse.lsp4j.ConfigurationParams
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
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import snyk.iac.IacResult
import snyk.oss.OssResult
import java.io.FileNotFoundException
import java.util.concurrent.CompletableFuture

class SnykLanguageClient(val project: Project) : LanguageClient {
    val logger = Logger.getInstance("Snyk Language Server")
    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)!!

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

        var ossIndicator: ProgressIndicator? = null
        var codeIndicator: ProgressIndicator? = null
        var iacIndicator: ProgressIndicator? = null

        when (snykScan.status) {
            "inProgress" -> {
                ProgressManager.getInstance()
                    .run(object : Task.Backgroundable(project, "Started ${snykScan.product} scan", false, DEAF) {
                        override fun run(indicator: ProgressIndicator) {
                            // Your background task code here
                            indicator.isIndeterminate = true
                            when (snykScan.product) {
                                "Snyk Open Source" -> ossIndicator = indicator
                                "Snyk Code" -> codeIndicator = indicator
                                "Snyk Infrastructure as Code" -> iacIndicator = indicator
                            }
                            scanPublisher.scanningStarted()
                        }
                    })
            }

            "success" -> {
                logger.info("Scan completed")
                when (snykScan.product) {
                    "oss" -> {
                        ossIndicator?.cancel()
                        scanPublisher.scanningOssFinished(getOssResult(snykScan))
                    }

                    "code" -> {
                        codeIndicator?.cancel()
                        scanPublisher.scanningSnykCodeFinished(getSnykCodeResult(snykScan))
                    }

                    "iac" -> {
                        iacIndicator?.cancel()
                        scanPublisher.scanningIacFinished(getSnykIaCResult(snykScan))
                    }
                }
            }
        }
    }

    private fun getSnykIaCResult(snykScan: SnykScanParams): IacResult {
        TODO("Not yet implemented")
    }

    private fun getSnykCodeResult(snykScan: SnykScanParams): SnykCodeResults? {
        check(snykScan.product == "code") { "Expected Snyk Code scan result" }
        val file2suggestions = mutableMapOf<SnykCodeFile, List<SuggestionForFile>>()
        snykScan.issues.groupBy { it.filePath }.forEach { (filePath, issues) ->
            val virtualFile =
                StandardFileSystems.local().refreshAndFindFileByPath(filePath) ?: throw FileNotFoundException(filePath)
            val suggestionForFiles = issues.map { issue ->
                SuggestionForFile(
                    issue.id,
                    issue.additionalData.rule,
                    issue.additionalData.message,
                    issue.title,
                    issue.additionalData.text,
                    when (issue.severity) {
                        "high" -> 0
                        "medium" -> 1
                        "low" -> 2
                        else -> -1
                    },
                    issue.additionalData.repoDatasetSize, // is this important?
                    getExampleCommitFixDescriptions(issue),
                    getExampleCommitFixes(issue),
                    getRanges(issue),
                    issue.additionalData.isSecurityType.ifTrue { mutableListOf("Security") } ?: mutableListOf(""),
                    emptyList(),
                    getCWEs(issue)
                )
            }.toMutableList()
            file2suggestions[SnykCodeFile(project, virtualFile)] = suggestionForFiles
        }
        return SnykCodeResults(file2suggestions)
    }

    private fun getOssResult(snykScan: SnykScanParams): OssResult {
        check(snykScan.product == "Snyk Open Source") { "Expected Snyk Open Source scan result" }
        TODO("Not yet implemented")
    }

    override fun configuration(configurationParams: ConfigurationParams?): CompletableFuture<MutableList<Any>> {
        return super.configuration(configurationParams)
    }

    override fun createProgress(params: WorkDoneProgressCreateParams?): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    override fun notifyProgress(params: ProgressParams?) {
        //
    }

    override fun logTrace(params: LogTraceParams?) {
        logger.info(params?.message)
    }

    override fun showMessage(messageParams: MessageParams?) {
        when (messageParams?.type) {
            MessageType.Error -> SnykBalloonNotificationHelper.showError(messageParams.message, project)
            MessageType.Warning -> SnykBalloonNotificationHelper.showWarn(messageParams.message, project)
            MessageType.Info -> SnykBalloonNotificationHelper.showInfo(messageParams.message, project)
            MessageType.Log -> logger.info(messageParams.message)
            null -> {}
        }
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> {
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

private fun getCWEs(issue: ScanIssue): MutableList<String> {
    return issue.additionalData.cwe?.toMutableList() ?: mutableListOf()
}

private fun getExampleCommitFixes(issue: ScanIssue): MutableList<ExampleCommitFix> {
    return issue.additionalData.exampleCommitFixes.map { fix ->
        ExampleCommitFix(
            fix.commitURL,
            fix.lines.map { ai.deepcode.javaclient.responses.ExampleLine(it.line, it.lineNumber, it.lineChange) }
        )
    }.toMutableList()
}

private fun getRanges(issue: ScanIssue): List<MyTextRange> {
    val ranges = mutableListOf<MyTextRange>()
    if (issue.additionalData.markers != null) {
        val markers = issue.additionalData.markers
        for (marker in markers) {
            ranges.add(MyTextRange(marker.msg?.get(0) ?: 0, marker.msg?.get(1) ?: 0))
        }
    }
    return ranges
}

private fun getExampleCommitFixDescriptions(issue: ScanIssue): MutableList<String> {
    return issue.additionalData.exampleCommitFixes.map { it.commitURL }.toMutableList()
}
