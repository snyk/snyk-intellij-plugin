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
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.ui.SnykBalloonNotifications
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel


@Service
class SnykTaskQueueService(val project: Project) {
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")

    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)

    private val cliDownloadPublisher
        get() = getSyncPublisher(project, SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher
        get() = getSyncPublisher(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    private var currentProgressIndicator: ProgressIndicator? = null

    fun getCurrentProgressIndicator(): ProgressIndicator? = currentProgressIndicator

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

                currentProgressIndicator = indicator

                ApplicationManager.getApplication().invokeAndWait {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }

                currentProgressIndicator = null
                indicator.checkCanceled()

                val settings = getApplicationSettingsStateService()

                if (settings.cliScanEnable) {
                    scheduleCliScan()
                }
                if (settings.snykCodeSecurityIssuesScanEnable || settings.snykCodeQualityIssuesScanEnable) {
                    object : Task.Backgroundable(project, "Checking if Snyk Code enabled for organisation...", true) {
                        override fun run(indicator: ProgressIndicator) {
                            settings.sastOnServerEnabled = service<SnykApiService>().sastOnServerEnabled ?: false
                            if (settings.sastOnServerEnabled) {
                                getSnykCode(project).scan()
                                scanPublisher?.scanningStarted()
                            } else {
                                settings.snykCodeSecurityIssuesScanEnable = false
                                settings.snykCodeQualityIssuesScanEnable = false
                                SnykBalloonNotifications.showSastForOrgEnablement(project)
                            }
                        }
                    }.queue()
                }

            }
        })
    }

    private fun scheduleCliScan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk CLI is scanning", true) {
            override fun run(indicator: ProgressIndicator) {

                val toolWindowPanel = project.service<SnykToolWindowPanel>()
                if (toolWindowPanel.currentCliResults != null) return

                currentProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                val cliResult: CliResult = getCli(project).scan()

                currentProgressIndicator = null
                if (project.isDisposed) return

                if (indicator.isCanceled) {
                    taskQueuePublisher?.stopped(wasCliRunning = true)
                } else {
                    if (cliResult.isSuccessful()) {
                        scanPublisher?.scanningCliFinished(cliResult)
                    } else {
                        scanPublisher?.scanningCliError(cliResult.error!!)
                    }
                }
            }
        })
    }

    fun downloadLatestRelease() {
        taskQueue.run(object : Task.Backgroundable(project, "Check Snyk CLI", true) {
            override fun run(indicator: ProgressIndicator) {
                cliDownloadPublisher?.checkCliExistsStarted()

                currentProgressIndicator = indicator

                val cliDownloader = service<SnykCliDownloaderService>()

                currentProgressIndicator = null
                indicator.checkCanceled()
                if (project.isDisposed) return

                if (!getCli(project).isCliInstalled()) {
                    cliDownloadPublisher?.cliDownloadStarted()

                    cliDownloader.downloadLatestRelease(indicator)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator)
                }

                cliDownloadPublisher?.checkCliExistsFinished()
            }
        })
    }

    fun stopScan() {
        val wasCliRunning = currentProgressIndicator?.isRunning == true
        currentProgressIndicator?.cancel()
        val wasSnykCodeRunning = isSnykCodeRunning(project)
        RunUtils.instance.cancelRunningIndicators(project)
        taskQueuePublisher?.stopped(wasCliRunning, wasSnykCodeRunning)
    }
}
