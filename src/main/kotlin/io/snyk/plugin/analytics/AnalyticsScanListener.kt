package io.snyk.plugin.analytics

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.core.SnykCodeFile
import snyk.common.SnykError
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult

@Service(Service.Level.PROJECT)
// FIXME
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

    private val snykScanListenerLS = object : SnykScanListenerLS {
        override fun scanningStarted() {
            TODO("Not yet implemented")
        }

        override fun scanningOssFinished(ossResult: OssResult) {
            TODO("Not yet implemented")
        }

        override fun scanningSnykCodeFinished(snykCodeResults: Map<SnykCodeFile, List<ScanIssue>>) {
            TODO("Not yet implemented")
        }

        override fun scanningIacFinished(iacResult: IacResult) {
            TODO("Not yet implemented")
        }

        override fun scanningOssError(snykError: SnykError) {
            TODO("Not yet implemented")
        }

        override fun scanningIacError(snykError: SnykError) {
            TODO("Not yet implemented")
        }

        override fun scanningSnykCodeError(snykError: SnykError) {
            TODO("Not yet implemented")
        }

        override fun scanningContainerFinished(containerResult: ContainerResult) {
            TODO("Not yet implemented")
        }

        override fun scanningContainerError(snykError: SnykError) {
            TODO("Not yet implemented")
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

        override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults) {
            val duration = System.currentTimeMillis() - start
            val product = "Snyk Code"
            val scanDoneEvent = getScanDoneEvent(
                duration,
                product,
                snykCodeResults.totalCriticalCount,
                snykCodeResults.totalErrorsCount,
                snykCodeResults.totalWarnsCount,
                snykCodeResults.totalInfosCount,
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

        // FIXME feature flag for LS
        project.messageBus.connect().subscribe(
            SnykScanListenerLS.SNYK_SCAN_TOPIC,
            snykScanListenerLS,
        )
    }
}
