package io.snyk.plugin.analytics

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.SnykCodeResults
import org.apache.commons.lang.SystemUtils
import snyk.common.SnykError
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult
import snyk.pluginInfo

@Service
class AnalyticsScanListener(val project: Project) {
    fun initScanListener() = project.messageBus.connect().subscribe(
        SnykScanListener.SNYK_SCAN_TOPIC,
        object : SnykScanListener {
            var start: Long = 0

            private fun getScanDoneEvent(
                duration: Long, product: String, critical: Int, high: Int, medium: Int, low: Int
            ): ScanDoneEvent {
                return ScanDoneEvent(
                    ScanDoneEvent.Data(
                        type = "analytics",
                        attributes = ScanDoneEvent.Attributes(
                            deviceId = pluginSettings().userAnonymousId,
                            application = ApplicationInfo.getInstance().fullApplicationName,
                            applicationVersion = ApplicationInfo.getInstance().fullVersion,
                            os = SystemUtils.OS_NAME,
                            arch = SystemUtils.OS_ARCH,
                            integrationName = pluginInfo.integrationName,
                            integrationVersion = pluginInfo.integrationVersion,
                            integrationEnvironment = pluginInfo.integrationEnvironment,
                            integrationEnvironmentVersion = pluginInfo.integrationEnvironmentVersion,
                            eventType = "Scan done",
                            status = "Succeeded",
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
                getSnykTaskQueueService(project)?.ls?.sendReportAnalyticsCommand(scanDoneEvent)
            }

            override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults?) {
                snykCodeResults?.let {
                    val scanDoneEvent = getScanDoneEvent(
                        System.currentTimeMillis() - start,
                        "Snyk Code",
                        snykCodeResults.totalCriticalCount,
                        snykCodeResults.totalErrorsCount,
                        snykCodeResults.totalWarnsCount,
                        snykCodeResults.totalInfosCount,
                    )
                    getSnykTaskQueueService(project)?.ls?.sendReportAnalyticsCommand(scanDoneEvent)
                }
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
                getSnykTaskQueueService(project)?.ls?.sendReportAnalyticsCommand(scanDoneEvent)
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

            override fun scanningContainerFinished(containerResult: ContainerResult) {
                val scanDoneEvent = getScanDoneEvent(
                    System.currentTimeMillis() - start,
                    "Snyk Container",
                    containerResult.criticalSeveritiesCount(),
                    containerResult.highSeveritiesCount(),
                    containerResult.mediumSeveritiesCount(),
                    containerResult.lowSeveritiesCount()
                )
                getSnykTaskQueueService(project)?.ls?.sendReportAnalyticsCommand(scanDoneEvent)
            }

            override fun scanningContainerError(snykError: SnykError) {
                // do nothing
            }
        },
    )
}
