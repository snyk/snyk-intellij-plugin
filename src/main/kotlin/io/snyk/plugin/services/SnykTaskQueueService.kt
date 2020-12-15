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
import io.snyk.plugin.events.SnykCliScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getCli


@Service
class SnykTaskQueueService(val project: Project) {
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")

    private val cliScanPublisher =
        project.messageBus.syncPublisher(SnykCliScanListener.CLI_SCAN_TOPIC)

    private val cliDownloadPublisher =
        project.messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher =
        project.messageBus.syncPublisher(SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    private var currentProgressIndicator: ProgressIndicator? = null

    fun getCurrentProgressIndicator(): ProgressIndicator? = currentProgressIndicator

    fun getTaskQueue() = taskQueue

    fun scan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                cliScanPublisher.scanningStarted()

                ApplicationManager.getApplication().invokeAndWait {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }

                indicator.checkCanceled()

                currentProgressIndicator = indicator

                indicator.checkCanceled()

                val cliResult: CliResult = getCli(project).scan()

                indicator.checkCanceled()

                if (cliResult.isSuccessful()) {
                    cliScanPublisher.scanningFinished(cliResult)
                } else {
                    cliScanPublisher.scanError(cliResult.error!!)
                }

                currentProgressIndicator = null
            }
        })
    }

    fun downloadLatestRelease() {
        taskQueue.run(object : Task.Backgroundable(project, "Check Snyk CLI", true) {
            override fun run(indicator: ProgressIndicator) {
                cliDownloadPublisher.checkCliExistsStarted()

                currentProgressIndicator = indicator

                val cliDownloader = service<SnykCliDownloaderService>()

                indicator.checkCanceled()

                if (!getCli(project).isCliInstalled()) {
                    cliDownloadPublisher.cliDownloadStarted()

                    cliDownloader.downloadLatestRelease(indicator)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator)
                }

                currentProgressIndicator = null

                cliDownloadPublisher.checkCliExistsFinished()
            }
        })
    }

    fun publishStoppedEvent() = taskQueuePublisher.stopped()
}
