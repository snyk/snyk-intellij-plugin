package io.snyk.plugin.services.download

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import io.snyk.plugin.getCliFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.io.IOException

class CliDownloaderErrorHandler {
    fun showErrorWithRetryAndContactAction(message: String, indicator: ProgressIndicator, project: Project) {
        SnykBalloonNotificationHelper.showError(message, project,
            NotificationAction.createSimple("Retry CLI download") {
                project.getService(SnykCliDownloaderService::class.java).downloadLatestRelease(indicator, project)
                runBackgroundableTask("Retry Snyk CLI Download", project, false) {
                    project.getService(SnykCliDownloaderService::class.java).downloadLatestRelease(it, project)
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
        try {
            runBackgroundableTask("Retry CLI Download", project, false) {
                val downloader = project.getService(SnykCliDownloaderService::class.java).downloader
                downloader.downloadFile(getCliFile(), indicator)
            }
        } catch (throwable: Throwable) { // we must catch throwable as IntelliJ could throw AssertionError
            // IntelliJ throws an exception if we log an error, and in this case it is just the retry that failed
            logger<CliDownloaderErrorHandler>().warn("Retry of downloading the Snyk CLI failed.", throwable)
            showErrorWithRetryAndContactAction(message, indicator, project)
        }
    }

    fun getNetworkErrorNotificationMessage(exception: IOException) =
        "The download of the Snyk CLI was interrupted by a network error (${exception.localizedMessage}). " +
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
