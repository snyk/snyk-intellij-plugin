package io.snyk.plugin.analytics

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykCodeScanListenerLS
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.core.SnykCodeFile
import snyk.common.SnykError
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult

@Service(Service.Level.PROJECT)
class AnalyticsScanListener(val project: Project) {
    fun getScanDoneEvent(
        duration: Long,
        product: String,
        critical: Int,
        high: Int,
        medium: Int,
        low: Int
    ): ScanDoneEvent {
        return ScanDoneEvent(
            ScanDoneEvent.Data(
                attributes = ScanDoneEvent.Attributes(
                    scanType = product,
                    uniqueIssueCount = ScanDoneEvent.UniqueIssueCount(
                        critical = critical,
                        high = high,
                        medium = medium,
                        low = low
                    ),
                    durationMs = "$duration",
                )
            )
        )
    }

    private val snykCodeScanListenerLS = object : SnykCodeScanListenerLS {
        var start = 0L
        override fun scanningStarted(snykScan: SnykScanParams) {
            start = System.currentTimeMillis()
        }

        override fun scanningSnykCodeFinished(snykCodeResults: Map<SnykCodeFile, List<ScanIssue>>) {
            val duration = System.currentTimeMillis() - start
            val product = "Snyk Code"
            val issues = snykCodeResults.values.flatten()
            val scanDoneEvent = getScanDoneEvent(
                duration,
                product,
                issues.count { it.getSeverityAsEnum() == Severity.CRITICAL },
                issues.count { it.getSeverityAsEnum() == Severity.HIGH },
                issues.count { it.getSeverityAsEnum() == Severity.MEDIUM },
                issues.count { it.getSeverityAsEnum() == Severity.LOW },
            )
            LanguageServerWrapper.getInstance().sendReportAnalyticsCommand(scanDoneEvent)
        }

        override fun scanningSnykCodeError(snykScan: SnykScanParams) {
            // do nothing
        }
    }

    val snykScanListener = object : SnykScanListener {
        var start: Long = 0

        override fun scanningStarted() {
            start = System.currentTimeMillis()
        }

        override fun scanningOssFinished(ossResult: OssResult) {
            val scanDoneEvent = getScanDoneEvent(
                System.currentTimeMillis() - start,
                "Snyk Open Source",
                ossResult.criticalSeveritiesCount(),
                ossResult.highSeveritiesCount(),
                ossResult.mediumSeveritiesCount(),
                ossResult.lowSeveritiesCount()
            )
            LanguageServerWrapper.getInstance().sendReportAnalyticsCommand(scanDoneEvent)
        }

        override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults?) {
            val duration = System.currentTimeMillis() - start
            val product = "Snyk Code"
            val scanDoneEvent = getScanDoneEvent(
                duration,
                product,
                snykCodeResults?.totalCriticalCount ?: 0,
                snykCodeResults?.totalErrorsCount ?: 0,
                snykCodeResults?.totalWarnsCount ?: 0,
                snykCodeResults?.totalInfosCount ?: 0,
            )
            LanguageServerWrapper.getInstance().sendReportAnalyticsCommand(scanDoneEvent)
        }

        override fun scanningIacFinished(iacResult: IacResult) {
            val scanDoneEvent = getScanDoneEvent(
                System.currentTimeMillis() - start,
                "Snyk IaC",
                iacResult.criticalSeveritiesCount(),
                iacResult.highSeveritiesCount(),
                iacResult.mediumSeveritiesCount(),
                iacResult.lowSeveritiesCount()
            )
            LanguageServerWrapper.getInstance().sendReportAnalyticsCommand(scanDoneEvent)
        }

        override fun scanningContainerFinished(containerResult: ContainerResult) {
            val scanDoneEvent = getScanDoneEvent(
                System.currentTimeMillis() - start,
                "Snyk Container",
                containerResult.criticalSeveritiesCount(),
                containerResult.highSeveritiesCount(),
                containerResult.mediumSeveritiesCount(),
                containerResult.lowSeveritiesCount()
            )
            LanguageServerWrapper.getInstance().sendReportAnalyticsCommand(scanDoneEvent)
        }

        override fun scanningOssError(snykError: SnykError) {
            // do nothing
        }

        override fun scanningIacError(snykError: SnykError) {
            // do nothing
        }

        override fun scanningSnykCodeError(snykError: SnykError) {
            // do nothing
        }

        override fun scanningContainerError(snykError: SnykError) {
            // do nothing
        }
    }

    fun initScanListener() {
        project.messageBus.connect().subscribe(
            SnykScanListener.SNYK_SCAN_TOPIC,
            snykScanListener,
        )

        if (isSnykCodeLSEnabled())
            project.messageBus.connect().subscribe(
                SnykCodeScanListenerLS.SNYK_SCAN_TOPIC,
                snykCodeScanListenerLS,
            )
    }
}
