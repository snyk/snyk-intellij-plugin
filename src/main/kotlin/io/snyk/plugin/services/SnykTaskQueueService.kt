package io.snyk.plugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.runInBackground
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.jetbrains.concurrency.runAsync
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanState
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service(Service.Level.PROJECT)
class SnykTaskQueueService(val project: Project) {
    private val logger = logger<SnykTaskQueueService>()

    private val cliDownloadPublisher
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private val taskQueuePublisher
        get() = getSyncPublisher(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC)

    fun connectProjectToLanguageServer(project: Project) {
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        languageServerWrapper.ensureLanguageServerInitialized()

        // wait for modules to be loaded and indexed so we can add all relevant content roots
        DumbService.getInstance(project).runWhenSmart {
            runAsync {
                try {
                    languageServerWrapper.addContentRoots(project)
                } catch (e: RuntimeException) {
                    logger.error("unable to add content roots for project $project", e)
                }
            }
        }
    }

    fun scan() {
        runInBackground("Snyk: triggering scan", project, true) {
            it.checkCanceled()
            it.text = "Snyk: checking if workspace is trusted"
            if (!confirmScanningAndSetWorkspaceTrustedStateIfNeeded(project)) return@runInBackground

            it.checkCanceled()
            it.text = "Snyk: saving all documents"
            ApplicationManager.getApplication().invokeAndWait {
                FileDocumentManager.getInstance().saveAllDocuments()
            }
            it.checkCanceled()
            it.text = "Snyk: waiting for CLI to be downloaded"
            waitUntilCliDownloadedIfNeeded()

            it.checkCanceled()
            it.text = "Snyk: triggering scan in language server"
            LanguageServerWrapper.getInstance(project).sendScanCommand()
        }
    }

    fun waitUntilCliDownloadedIfNeeded() {
        if (!pluginSettings().manageBinariesAutomatically) return
        if (project.isDisposed || ApplicationManager.getApplication().isDisposed) return

        val completed = CompletableFuture<Unit>()
        val connection = ApplicationManager.getApplication().messageBus.connect(project)
        try {
            connection.subscribe(
                SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
                object : SnykCliDownloadListener {
                    override fun checkCliExistsFinished() {
                        completed.complete(Unit)
                    }
                }
            )
            downloadLatestRelease()
            while (true) {
                if (project.isDisposed || ApplicationManager.getApplication().isDisposed) return
                try {
                    completed.get(1, TimeUnit.SECONDS)
                    return
                } catch (_: TimeoutException) {
                    // keep waiting
                }
            }
        } finally {
            connection.disconnect()
        }
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

        runInBackground("Snyk: Check CLI presence", project, true) { indicator ->
            indicator.checkCanceled()
            cliDownloadPublisher.checkCliExistsStarted()
            if (project.isDisposed) return@runInBackground

            indicator.checkCanceled()
            if (!isCliInstalled()) {
                cliDownloader.downloadLatestRelease(indicator, project)
            } else {
                cliDownloader.cliSilentAutoUpdate(indicator, project, force)
            }
            cliDownloadPublisher.checkCliExistsFinished()
        }
    }

    fun stopScan() {
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)

        if (languageServerWrapper.isInitialized) {
            languageServerWrapper.languageClient.progressManager.cancelProgresses()
            ScanState.scanInProgress.clear()
        }
        taskQueuePublisher?.stopped()
    }

}
