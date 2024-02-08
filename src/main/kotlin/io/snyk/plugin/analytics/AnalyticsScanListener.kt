package io.snyk.plugin.analytics

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.snykcode.SnykCodeResults
import snyk.common.SnykError
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult

@Service(Service.Level.PROJECT)
class AnalyticsScanListener(val project: Project) {
    fun getScanDoneEvent(
        duration: Long, product: String, critical: Int, high: Int, medium: Int, low: Int
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
            val scanDoneEvent = if (snykCodeResults != null) {
                getScanDoneEvent(
                    duration,
                    product,
                    snykCodeResults.totalCriticalCount,
                    snykCodeResults.totalErrorsCount,
                    snykCodeResults.totalWarnsCount,
                    snykCodeResults.totalInfosCount,
                )
            } else {
                getScanDoneEvent(duration, product, 0, 0, 0, 0)
            }
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

    fun initScanListener() = project.messageBus.connect().subscribe(
        SnykScanListener.SNYK_SCAN_TOPIC,
        snykScanListener,
    )
}
