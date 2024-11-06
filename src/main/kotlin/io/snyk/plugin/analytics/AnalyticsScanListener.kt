package io.snyk.plugin.analytics

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.toVirtualFile
import snyk.common.SnykError
import snyk.common.lsp.analytics.AnalyticsEvent
import snyk.common.lsp.analytics.ScanDoneEvent
import snyk.container.ContainerResult

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
                    path = project.basePath?.toVirtualFile()?.toNioPath()?.toFile()?.absolutePath.orEmpty()
                )
            )
        )
    }

    val snykScanListener = object : SnykScanListener {
        var start: Long = 0

        override fun scanningStarted() {
            start = System.currentTimeMillis()
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
            AnalyticsSender.getInstance().logEvent(scanDoneEvent)
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
        if (!pluginSettings().pluginInstalledSent) {
            val event = AnalyticsEvent("plugin installed", listOf("install"))
            AnalyticsSender.getInstance().logEvent(event) {
                pluginSettings().pluginInstalledSent = true
            }
        }
    }
}
