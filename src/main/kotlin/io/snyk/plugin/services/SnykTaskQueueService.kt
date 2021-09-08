package io.snyk.plugin.services

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getContainerService
import io.snyk.plugin.getIacService
import io.snyk.plugin.getOssService
import io.snyk.plugin.getSnykCode
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.ui.SnykBalloonNotifications
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.jetbrains.annotations.TestOnly
import snyk.oss.OssResult

private val LOG = logger<SnykTaskQueueService>()

@Service
class SnykTaskQueueService(val project: Project) {
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")
    private val taskQueueIacContainer = BackgroundTaskQueue(project, "Snyk: Iac - Container")

    private val settings = getApplicationSettingsStateService()

    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)

    private val cliDownloadPublisher
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher
        get() = getSyncPublisher(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    private var ossScanProgressIndicator: ProgressIndicator? = null
    private var iacScanProgressIndicator: ProgressIndicator? = null
    private var containerScanProgressIndicator: ProgressIndicator? = null

    fun getOssScanProgressIndicator(): ProgressIndicator? = ossScanProgressIndicator
    fun getIacScanProgressIndicator(): ProgressIndicator? = iacScanProgressIndicator
    fun getContainerScanProgressIndicator(): ProgressIndicator? = containerScanProgressIndicator

    @TestOnly
    fun getTaskQueue() = taskQueue

    fun scheduleRunnable(title: String, runnable: (indicator: ProgressIndicator) -> Unit) {
        taskQueue.run(object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                runnable.invoke(indicator)
            }
        })
    }

    fun scan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk wait for changed files to be saved on disk", true) {
            override fun run(indicator: ProgressIndicator) {
                ApplicationManager.getApplication().invokeAndWait {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }
                indicator.checkCanceled()

                if (settings.ossScanEnable) {
                    if (!getOssService(project).isCliInstalled()) downloadLatestRelease()
                    scheduleOssScan()
                }
                if (settings.snykCodeSecurityIssuesScanEnable || settings.snykCodeQualityIssuesScanEnable) {
                    scheduleSnykCodeScan()
                }

                //TODO(pavel): replace with settings
                val iacEnabled = true
                if (iacEnabled) {
                    scheduleIacScan()
                }

                //TODO(pavel): replace with settings
                val containerEnabled = true
                if (containerEnabled) {
                    scheduleContainerScan()
                }
            }
        })
    }

    private fun scheduleSnykCodeScan() {
        object : Task.Backgroundable(project, "Checking if Snyk Code enabled for organisation...", true) {
            override fun run(indicator: ProgressIndicator) {
                settings.sastOnServerEnabled = service<SnykApiService>().sastOnServerEnabled
                when (settings.sastOnServerEnabled) {
                    true -> {
                        getSnykCode(project).scan()
                        scanPublisher?.scanningStarted()
                    }
                    false -> {
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                        SnykBalloonNotifications.showSastForOrgEnablement(project)
                    }
                    null -> {
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                        SnykBalloonNotifications.showNetworkErrorAlert(project)
                    }
                }
            }
        }.queue()
    }

    private fun scheduleOssScan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk Open Source is scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!getOssService(project).isCliInstalled()) return

                val toolWindowPanel = project.service<SnykToolWindowPanel>()
                if (toolWindowPanel.currentOssResults != null) return

                ossScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                val ossResult: OssResult = getOssService(project).scan()

                ossScanProgressIndicator = null
                if (project.isDisposed) return

                if (indicator.isCanceled) {
                    taskQueuePublisher?.stopped(wasOssRunning = true)
                } else {
                    if (ossResult.isSuccessful()) {
                        scanPublisher?.scanningOssFinished(ossResult)
                    } else {
                        scanPublisher?.scanningOssError(ossResult.error!!)
                    }
                }
            }
        })
    }

    private fun scheduleIacScan() {
        taskQueueIacContainer.run(object : Task.Backgroundable(
            project,
            "Snyk Infrastructure as Code is scanning",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val toolWindowPanel = project.service<SnykToolWindowPanel>()
                if (toolWindowPanel.currentIacResult != null) return

                LOG.info("Starting IaC scan")
                iacScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                val iacResult = getIacService(project).scan()

                iacScanProgressIndicator = null
                if (project.isDisposed) return

                if (indicator.isCanceled) {
                    LOG.warn("cancel IaC scan")
                    taskQueuePublisher?.stopped(wasIacRunning = true)
                } else {
                    if (iacResult.isSuccessful()) {
                        LOG.warn("IaC result: ->")
                        iacResult.allCliIssues?.forEach {
                            LOG.warn("  ${it.targetFile}, ${it.infrastructureAsCodeIssues.size} issues")
                        }
                        scanPublisher?.scanningIacFinished(iacResult)
                    } else {
                        scanPublisher?.scanningIacError(iacResult.error!!)
                    }
                }
                LOG.info("IaC scan completed")
            }
        })
    }

    private fun scheduleContainerScan() {
        taskQueueIacContainer.run(object : Task.Backgroundable(project, "Snyk Container is scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                val toolWindowPanel = project.service<SnykToolWindowPanel>()
                //if (toolWindowPanel.currentContainerResult != null) return

                val iacResult = toolWindowPanel.currentIacResult
                if (iacResult != null) {
                    containerScanProgressIndicator = indicator
                    scanPublisher?.scanningStarted()
                    toolWindowPanel.currentContainerResult = null
                    val containerResult = getContainerService(project).scan(iacResult)

                    containerScanProgressIndicator = null
                    if (project.isDisposed) return

                    if (indicator.isCanceled) {
                        LOG.warn("cancel Container scan")
                        taskQueuePublisher?.stopped(wasContainerRunning = true)
                    } else {
                        if (containerResult.isSuccessful()) {
                            scanPublisher?.scanningContainerFinished(containerResult)
                        } else {
                            scanPublisher?.scanningContainerError(containerResult.error!!)
                        }
                    }
                } else {
                    LOG.warn("IaC result is null, nothing to scan")
                }

                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        })
    }

    fun downloadLatestRelease() {
        taskQueue.run(object : Task.Backgroundable(project, "Check Snyk CLI presence", true) {
            override fun run(indicator: ProgressIndicator) {
                cliDownloadPublisher.checkCliExistsStarted()
                val cliDownloader = service<SnykCliDownloaderService>()
                if (project.isDisposed) return

                if (!getOssService(project).isCliInstalled()) {
                    cliDownloader.downloadLatestRelease(indicator)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator)
                }
                cliDownloadPublisher.checkCliExistsFinished()
            }
        })
    }

    fun stopScan() {
        val wasOssRunning = ossScanProgressIndicator?.isRunning == true
        ossScanProgressIndicator?.cancel()
        val wasSnykCodeRunning = isSnykCodeRunning(project)
        RunUtils.instance.cancelRunningIndicators(project)
        val wasIacRunning = iacScanProgressIndicator?.isRunning == true
        taskQueuePublisher?.stopped(wasOssRunning, wasSnykCodeRunning, wasIacRunning)
    }
}
