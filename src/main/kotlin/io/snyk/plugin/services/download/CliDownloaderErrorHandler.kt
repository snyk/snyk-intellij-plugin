package io.snyk.plugin.services.download

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.io.IOException

class CliDownloaderErrorHandler {
    fun showErrorWithRetryAndContactAction(message: String, project: Project) {
        SnykBalloonNotificationHelper.showError(message, project,
            NotificationAction.createSimple("Retry CLI download") {
                runBackgroundableTask("Retry Snyk CLI Download", project, true) {
                    getSnykCliDownloaderService().downloadLatestRelease(it, project)
                }
            },
            NotificationAction.createSimple("Contact support...") {
                BrowserUtil.browse("https://snyk.io/contact-us/?utm_source=JETBRAINS_IDE")
            })
    }

    fun handleIOException(exception: IOException, indicator: ProgressIndicator, project: Project) {
        retryDownload(project, indicator, getNetworkErrorNotificationMessage(exception))
    }

    private fun retryDownload(project: Project, indicator: ProgressIndicator, message: String) {
        runBackgroundableTask("Retry Snyk CLI Download", project, false) {
            val cliDownloaderService = project.getService(SnykCliDownloaderService::class.java)
            try {
                // not using the service here to not causing an endless recursion (the service triggers a retry)
                val downloader = cliDownloaderService.downloader
                downloader.downloadFile(getCliFile(), downloader.expectedSha(), indicator)
            } catch (throwable: Throwable) { // we must catch throwable as IntelliJ could throw AssertionError
                // IntelliJ throws an exception if we log an error, and in this case it is just the retry that failed
                logger<CliDownloaderErrorHandler>().warn("Retry of downloading the Snyk CLI failed.", throwable)
                showErrorWithRetryAndContactAction(message, project)
            }
        }
    }

    fun getNetworkErrorNotificationMessage(exception: IOException) =
        "The download of the Snyk CLI was interrupted by an error (${exception.localizedMessage}). " +
            "Do you want to try again?"

    fun handleHttpStatusException(exception: HttpRequests.HttpStatusException, project: Project) {
        SnykBalloonNotificationHelper.showError(
            getHttpStatusErrorNotificationMessage(exception),
            project,
            NotificationAction.createSimple("Contact support...") {
                BrowserUtil.browse("https://snyk.io/contact-us/?utm_source=JETBRAINS_IDE")
            })
    }

    fun getHttpStatusErrorNotificationMessage(exception: HttpRequests.HttpStatusException) =
        "The download request of the current Snyk CLI was not successful (${exception.localizedMessage})."

    fun handleChecksumVerificationException(
        e: ChecksumVerificationException,
        indicator: ProgressIndicator,
        project: Project
    ) {
        retryDownload(project, indicator, getChecksumFailedNotificationMessage(e))
    }

    fun getChecksumFailedNotificationMessage(exception: ChecksumVerificationException) =
        "The download of the Snyk CLI was not successful. The integrity check failed (${exception.localizedMessage})."
}
