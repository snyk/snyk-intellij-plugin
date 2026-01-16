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
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsync
import io.snyk.plugin.publishAsyncApp
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

    private companion object {
        private val WAIT_FOR_CLI_DOWNLOAD_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(20)
    }

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
            // Verify CLI is actually available before proceeding
            if (!isCliInstalled()) {
                logger.warn("CLI not available after download attempt, cannot start scan")
                SnykBalloonNotificationHelper.showError(
                    "Snyk CLI is not available. Please check your network connection and try again.",
                    project
                )
                return@runInBackground
            }

            it.checkCanceled()
            it.text = "Snyk: triggering scan in language server"
            LanguageServerWrapper.getInstance(project).sendScanCommand()
        }
    }

    fun waitUntilCliDownloadedIfNeeded() {
        if (project.isDisposed || ApplicationManager.getApplication().isDisposed) return
        // Don't skip when manageBinariesAutomatically is false - downloadLatestRelease()
        // handles that case and shows an appropriate notification if CLI is missing

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
            val deadline = System.currentTimeMillis() + WAIT_FOR_CLI_DOWNLOAD_TIMEOUT_MS
            while (!project.isDisposed && !ApplicationManager.getApplication().isDisposed) {
                try {
                    completed.get(1, TimeUnit.SECONDS)
                    return
                } catch (_: TimeoutException) {
                    if (System.currentTimeMillis() >= deadline) {
                        logger.warn("Timed out waiting for CLI download to finish")
                        return
                    }
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
            // Signal completion so waitUntilCliDownloadedIfNeeded() doesn't wait indefinitely
            publishAsyncApp(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC) { checkCliExistsFinished() }
            return
        }

        runInBackground("Snyk: Check CLI presence", project, true) { indicator ->
            try {
                indicator.checkCanceled()
                publishAsyncApp(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC) { checkCliExistsStarted() }
                if (project.isDisposed) return@runInBackground

                indicator.checkCanceled()
                val cliInstalled = isCliInstalled()
                logger.debug("CLI check: isCliInstalled=$cliInstalled, force=$force")
                if (!cliInstalled) {
                    logger.debug("CLI not installed, triggering download")
                    cliDownloader.downloadLatestRelease(indicator, project)
                } else {
                    // Verify CLI integrity before using it
                    val integrityOk = cliDownloader.verifyCliIntegrity(project)
                    logger.debug("CLI integrity check: integrityOk=$integrityOk")
                    if (force || !integrityOk) {
                        logger.debug("Triggering download due to force=$force or integrity failure")
                        cliDownloader.downloadLatestRelease(indicator, project)
                    } else {
                        logger.debug("CLI installed and verified, checking for updates")
                        cliDownloader.cliSilentAutoUpdate(indicator, project, force)
                    }
                }
            } finally {
                logger.debug("CLI check finished, isCliInstalled=${isCliInstalled()}")
                publishAsyncApp(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC) { checkCliExistsFinished() }
            }
        }
    }

    fun stopScan() {
        logger.debug("stopScan called")
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)

        if (languageServerWrapper.isInitialized) {
            languageServerWrapper.languageClient.progressManager.cancelProgresses()
            ScanState.scanInProgress.clear()
        }
        publishAsync(project, SnykTaskQueueListener.TASK_QUEUE_TOPIC) { stopped() }
    }

}
