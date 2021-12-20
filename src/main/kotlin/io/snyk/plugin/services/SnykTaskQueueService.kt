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
import io.snyk.plugin.getIacService
import io.snyk.plugin.getOssService
import io.snyk.plugin.getSnykCode
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.download.SnykCliDownloaderService
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.ui.SnykBalloonNotifications
import org.jetbrains.annotations.TestOnly
import snyk.common.SnykError
import snyk.oss.OssResult

private val LOG = logger<SnykTaskQueueService>()

@Service
class SnykTaskQueueService(val project: Project) {
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")
    private val taskQueueIac = BackgroundTaskQueue(project, "Snyk: Iac")

    private val settings
        get() = pluginSettings()

    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)

    private val cliDownloadPublisher
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher
        get() = getSyncPublisher(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    var ossScanProgressIndicator: ProgressIndicator? = null
        private set

    var iacScanProgressIndicator: ProgressIndicator? = null
        private set

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
                    if (getOssService(project)?.isCliInstalled() == false) downloadLatestRelease()
                    scheduleOssScan()
                }
                if (settings.snykCodeSecurityIssuesScanEnable || settings.snykCodeQualityIssuesScanEnable) {
                    scheduleSnykCodeScan()
                }
                if (isIacEnabled() && settings.iacScanEnabled) {
                    //fixme: downloadLatestRelease() should check if downloading is in process
                    if (getIacService(project)?.isCliInstalled() == false) downloadLatestRelease()
                    scheduleIacScan()
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
                        getSnykCode(project)?.scan()
                        scanPublisher?.scanningStarted()
                    }
                    false -> {
                        SnykBalloonNotifications.showSastForOrgEnablement(project)
                        scanPublisher?.scanningSnykCodeError(
                            SnykError(SnykBalloonNotifications.sastForOrgEnablementMessage, project.basePath ?: "")
                        )
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                    }
                    null -> {
                        SnykBalloonNotifications.showNetworkErrorAlert(project)
                        scanPublisher?.scanningSnykCodeError(
                            SnykError(SnykBalloonNotifications.networkErrorAlertMessage, project.basePath ?: "")
                        )
                        // todo(artsiom): shell we do it `false` here?
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                    }
                }
            }
        }.queue()
    }

    private fun scheduleOssScan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk Open Source is scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                val ossService = getOssService(project) ?: return
                if (!ossService.isCliInstalled()) return

                if (getSnykToolWindowPanel(project)?.currentOssResults != null) return

                ossScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                val ossResult: OssResult = ossService.scan()

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
        taskQueueIac.run(object : Task.Backgroundable(
            project,
            "Snyk Infrastructure as Code is scanning",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                val toolWindowPanel = getSnykToolWindowPanel(project) ?: return
                if (toolWindowPanel.currentIacResult != null && !toolWindowPanel.iacScanNeeded) return
                LOG.debug("Starting IaC scan")
                iacScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                toolWindowPanel.currentIacResult = null
                val iacResult = getIacService(project)?.scan() ?: return

                iacScanProgressIndicator = null
                if (project.isDisposed) return

                if (indicator.isCanceled) {
                    LOG.debug("cancel IaC scan")
                    taskQueuePublisher?.stopped(wasIacRunning = true)
                } else {
                    if (iacResult.isSuccessful()) {
                        LOG.debug("IaC result: ->")
                        iacResult.allCliIssues?.forEach {
                            LOG.debug("  ${it.targetFile}, ${it.infrastructureAsCodeIssues.size} issues")
                        }
                        scanPublisher?.scanningIacFinished(iacResult)
                    } else {
                        scanPublisher?.scanningIacError(iacResult.error!!)
                    }
                }
                LOG.debug("IaC scan completed")
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

                if (getOssService(project)?.isCliInstalled() == false) {
                    cliDownloader.downloadLatestRelease(indicator, project)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator, project)
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
        iacScanProgressIndicator?.cancel()

        taskQueuePublisher?.stopped(wasOssRunning, wasSnykCodeRunning, wasIacRunning)
    }
}
