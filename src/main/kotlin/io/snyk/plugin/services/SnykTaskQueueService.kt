package io.snyk.plugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getContainerService
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.jetbrains.annotations.TestOnly
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanState
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded

@Service(Service.Level.PROJECT)
class SnykTaskQueueService(val project: Project) {
    private val logger = logger<SnykTaskQueueService>()
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")
    private val taskQueueContainer = BackgroundTaskQueue(project, "Snyk: Container")

    private val settings
        get() = pluginSettings()

    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)

    private val cliDownloadPublisher
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher
        get() = getSyncPublisher(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    var containerScanProgressIndicator: ProgressIndicator? = null
        private set

    @TestOnly
    fun getTaskQueue() = taskQueue

    fun connectProjectToLanguageServer(project: Project) {
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.ensureLanguageServerInitialized()

        // wait for modules to be loaded and indexed so we can add all relevant content roots
        DumbService.getInstance(project).runWhenSmart {
            languageServerWrapper.addContentRoots(project)
        }
    }

    fun scan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk: triggering scan", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!confirmScanningAndSetWorkspaceTrustedStateIfNeeded(project)) return

                ApplicationManager.getApplication().invokeAndWait {
                    FileDocumentManager.getInstance().saveAllDocuments()
                }
                indicator.checkCanceled()
                waitUntilCliDownloadedIfNeeded()
                indicator.checkCanceled()

                LanguageServerWrapper.getInstance().sendScanCommand(project)

                scheduleContainerScan()
            }
        })
    }

    fun waitUntilCliDownloadedIfNeeded() {
        downloadLatestRelease()
        do {
            Thread.sleep(WAIT_FOR_DOWNLOAD_MILLIS)
        } while (isCliDownloading())
    }

    fun scheduleContainerScan() {
        if (!settings.containerScanEnabled) return
        taskQueueContainer.run(object : Task.Backgroundable(project, "Snyk Container is scanning...", true) {
            override fun run(indicator: ProgressIndicator) {
                if (!isCliInstalled()) return
                val snykCachedResults = getSnykCachedResults(project) ?: return
                if (snykCachedResults.currentContainerResult?.rescanNeeded == false) return
                logger.debug("Starting Container scan")
                containerScanProgressIndicator = indicator
                scanPublisher?.scanningStarted()

                snykCachedResults.currentContainerResult = null
                val containerResult = try {
                    getContainerService(project)?.scan()
                } finally {
                    containerScanProgressIndicator = null
                }
                if (containerResult == null || project.isDisposed) return

                if (indicator.isCanceled) {
                    logger.debug("cancel container scan")
                    taskQueuePublisher?.stopped()
                } else {
                    if (containerResult.isSuccessful()) {
                        logger.debug("Container result: ->")
                        containerResult.allCliIssues?.forEach {
                            logger.debug("  ${it.imageName}, ${it.vulnerabilities.size} issues")
                        }
                        scanPublisher?.scanningContainerFinished(containerResult)
                    } else {
                        scanPublisher?.scanningContainerError(containerResult.getFirstError()!!)
                    }
                }
                logger.debug("Container scan completed")
                refreshAnnotationsForOpenFiles(project)
            }
        })
    }

    fun downloadLatestRelease(force: Boolean = false) {
        // abort even before submitting a task
        if (project.isDisposed || ApplicationManager.getApplication().isDisposed) return
        val cliDownloader = getSnykCliDownloaderService()
        if (!pluginSettings().manageBinariesAutomatically) {
            if (!isCliInstalled()) {
                val msg =
                    "The plugin cannot scan without Snyk CLI, but automatic download is disabled. " +
                        "Please put a Snyk CLI executable in ${pluginSettings().cliPath} and retry."
                SnykBalloonNotificationHelper.showError(msg, project)
                // no need to cancel the indicator here, as isCLIDownloading() will return false
            }
            // no need to cancel the indicator here, as isCliInstalled() will return false
            cliDownloader.stopCliDownload()
            return
        }

        taskQueue.run(object : Task.Backgroundable(project, "Check Snyk CLI presence", true) {
            override fun run(indicator: ProgressIndicator) {
                cliDownloadPublisher.checkCliExistsStarted()
                if (project.isDisposed) return

                if (!isCliInstalled()) {
                    cliDownloader.downloadLatestRelease(indicator, project)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator, project, force)
                }
                cliDownloadPublisher.checkCliExistsFinished()
            }
        })
    }

    fun stopScan() {
        val languageServerWrapper = LanguageServerWrapper.getInstance()

        if (languageServerWrapper.isInitialized) {
            languageServerWrapper.languageClient.progressManager.cancelProgresses()
            ScanState.scanInProgress.clear()
        }

        containerScanProgressIndicator?.cancel()
        taskQueuePublisher?.stopped()
    }

    companion object {
        private const val WAIT_FOR_DOWNLOAD_MILLIS = 1000L
    }
}
