package io.snyk.plugin.services.download

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.download.HttpRequestHelper.createRequest
import snyk.common.lsp.LanguageServerWrapper
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.TimeUnit

@Service
class SnykCliDownloaderService {

    companion object {
        const val NUMBER_OF_DAYS_BETWEEN_RELEASE_CHECK = 4
    }

    var downloader = CliDownloader()
    var errorHandler = CliDownloaderErrorHandler()

    private val cliDownloadPublisher
        get() = ApplicationManager.getApplication().messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)

    private var currentProgressIndicator: ProgressIndicator? = null

    fun isCliDownloading() = currentProgressIndicator != null && !ApplicationManager.getApplication().isDisposed

    fun stopCliDownload() = currentProgressIndicator?.let {
        it.cancel()
        currentProgressIndicator = null
    }

    fun requestLatestReleasesInformation(): String? {
        if (!pluginSettings().manageBinariesAutomatically) return null
        return try {
            createRequest(CliDownloader.LATEST_RELEASES_URL).readString()
        } catch (ignore: Exception) {
            logger<SnykCliDownloaderService>().warn(ignore)
            null
        }
    }

    fun downloadLatestRelease(indicator: ProgressIndicator, project: Project) {
        currentProgressIndicator = indicator
        cliDownloadPublisher.cliDownloadStarted()
        indicator.isIndeterminate = true
        var succeeded = false
        val cliFile = getCliFile()
        val latestRelease: String
        try {
            latestRelease = requestLatestReleasesInformation() ?: ""

            if (latestRelease.isEmpty()) {
                val failedMsg = "Failed to fetch the latest Snyk CLI release info. " +
                    "Please retry in a few minutes or contact support if the issue persists."
                errorHandler.showErrorWithRetryAndContactAction(failedMsg, project)
                return
            }

            indicator.text = "Downloading latest Snyk CLI release..."
            indicator.checkCanceled()

            val languageServerWrapper = LanguageServerWrapper.getInstance()
            try {
                if (languageServerWrapper.isInitialized) {
                    try {
                        languageServerWrapper.shutdown()
                    } catch (e: RuntimeException) {
                        logger<SnykCliDownloaderService>()
                            .warn("Language server shutdown for download took too long, couldn't shutdown", e)
                    }
                }
                downloader.downloadFile(cliFile, latestRelease, indicator)
                pluginSettings().cliVersion = latestRelease
                pluginSettings().lastCheckDate = Date()
                succeeded = true
            } catch (e: HttpRequests.HttpStatusException) {
                errorHandler.handleHttpStatusException(e, project)
            } catch (e: IOException) {
                errorHandler.handleIOException(e, latestRelease, indicator, project)
            } catch (e: ChecksumVerificationException) {
                errorHandler.handleChecksumVerificationException(e, latestRelease, indicator, project)
            } finally {
                if (succeeded) languageServerWrapper.ensureLanguageServerInitialized() else stopCliDownload()
            }
        } finally {
            cliDownloadPublisher.cliDownloadFinished(succeeded)
            stopCliDownload()
        }
    }


    /**
     * Check if the CLI version is outdated and download the latest release if needed.
     *
     * Scenarios:
     * 1. No CLI installed -> download
     * 2. CLI installed and current LS protocol version matches required version
     *   - check if 4 days passed since last check
     *   - if yes -> download
     *   - if no -> do nothing
     * 3. CLI installed and current LS protocol version does not match required version -> download
     * 4. CLI installed, more than 4 days have passed and new version available -> download
     * 5. force param is set - always download
     *
     * @param indicator - progress indicator
     * @param project - current project
     * @param force - force the download
     */
    fun cliSilentAutoUpdate(indicator: ProgressIndicator, project: Project, force: Boolean = false) {
        if (force || isFourDaysPassedSinceLastCheck() || !matchesRequiredLsProtocolVersion()) {
            val latestReleaseInfo = requestLatestReleasesInformation()

            indicator.checkCanceled()

            val settings = pluginSettings()

            if (
                !latestReleaseInfo.isNullOrEmpty() &&
                isNewVersionAvailable(settings.cliVersion, latestReleaseInfo)
            ) {
                downloadLatestRelease(indicator, project)
                settings.lastCheckDate = Date()
            }
        }
    }

    private fun matchesRequiredLsProtocolVersion(): Boolean {
        return pluginSettings().currentLSProtocolVersion == pluginSettings().requiredLsProtocolVersion
    }

    fun isFourDaysPassedSinceLastCheck(): Boolean {
        val previousDate = pluginSettings().getLastCheckDate() ?: return true

        return ChronoUnit.DAYS.between(previousDate, LocalDate.now()) >= NUMBER_OF_DAYS_BETWEEN_RELEASE_CHECK
    }

    fun isNewVersionAvailable(currentCliVersion: String?, newCliVersion: String?): Boolean {
        val cliVersionsNullOrEmpty =
            currentCliVersion == null || newCliVersion == null ||
                currentCliVersion.isEmpty() || newCliVersion.isEmpty()

        if (cliVersionsNullOrEmpty) return true

        return currentCliVersion != newCliVersion
    }
}
