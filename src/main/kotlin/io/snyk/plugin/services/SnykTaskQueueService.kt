package io.snyk.plugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCli
import io.snyk.plugin.getSnykCode
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel


@Service
class SnykTaskQueueService(val project: Project) {
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")

    private val scanPublisher =
        project.messageBus.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC)

    private val cliDownloadPublisher =
        project.messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher =
        project.messageBus.syncPublisher(SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    private var currentProgressIndicator: ProgressIndicator? = null

    fun getCurrentProgressIndicator(): ProgressIndicator? = currentProgressIndicator

    fun getTaskQueue() = taskQueue

    fun scheduleRunnable (title: String, runnable: (indicator: ProgressIndicator) -> Unit) {
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
                scanPublisher.scanningStarted()

                ApplicationManager.getApplication().invokeAndWait {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }

                currentProgressIndicator = null
                indicator.checkCanceled()

                val settings = getApplicationSettingsStateService()

                if (settings.cliScanEnable) {
                    scheduleCliScan()
                }
                if (settings.snykCodeScanEnable || settings.snykCodeQualityIssuesScanEnable) {
                    getSnykCode(project).scan()
                }

            }
        })
    }

    private fun scheduleCliScan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk CLI is scanning", true) {
            override fun run(indicator: ProgressIndicator) {

                currentProgressIndicator = indicator

                val toolWindowPanel = project.service<SnykToolWindowPanel>()

                val cliResult: CliResult = toolWindowPanel.currentCliResults ?: getCli(project).scan()

                currentProgressIndicator = null
                if (project.isDisposed) return

                if (indicator.isCanceled) {
                    taskQueuePublisher.stopped()
                } else {
                    if (cliResult.isSuccessful()) {
                        scanPublisher.scanningCliFinished(cliResult)
                    } else {
                        scanPublisher.scanError(cliResult.error!!)
                    }
                }
            }
        })
    }

    fun downloadLatestRelease() {
        taskQueue.run(object : Task.Backgroundable(project, "Check Snyk CLI", true) {
            override fun run(indicator: ProgressIndicator) {
                cliDownloadPublisher.checkCliExistsStarted()

                currentProgressIndicator = indicator

                val cliDownloader = service<SnykCliDownloaderService>()

                currentProgressIndicator = null
                indicator.checkCanceled()
                if (project.isDisposed) return

                if (!getCli(project).isCliInstalled()) {
                    cliDownloadPublisher.cliDownloadStarted()

                    cliDownloader.downloadLatestRelease(indicator)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator)
                }

                cliDownloadPublisher.checkCliExistsFinished()
            }
        })
    }

    fun stopScan() {
        currentProgressIndicator?.cancel()
        RunUtils.instance.cancelRunningIndicators(project)
        taskQueuePublisher.stopped()
    }
}
