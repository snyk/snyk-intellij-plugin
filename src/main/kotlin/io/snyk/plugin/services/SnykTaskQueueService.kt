package io.snyk.plugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.snyk.plugin.*
import snyk.oss.OssResult
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.ui.SnykBalloonNotifications
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.jetbrains.annotations.TestOnly

@Service
class SnykTaskQueueService(val project: Project) {
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")

    private val settings = getApplicationSettingsStateService()

    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)

    private val cliDownloadPublisher
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher
        get() = getSyncPublisher(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    private var ossScanProgressIndicator: ProgressIndicator? = null

    fun getOssScanProgressIndicator(): ProgressIndicator? = ossScanProgressIndicator

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
        taskQueuePublisher?.stopped(wasOssRunning, wasSnykCodeRunning)
    }
}
