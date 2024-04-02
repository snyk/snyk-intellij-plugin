package io.snyk.plugin.services.download

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.download.HttpRequestHelper.createRequest
import io.snyk.plugin.tail
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

    private var latestReleaseInfo: LatestReleaseInfo? = null

    private var currentProgressIndicator: ProgressIndicator? = null

    fun isCliDownloading() = currentProgressIndicator != null

    fun stopCliDownload() = currentProgressIndicator?.let {
        it.cancel()
        currentProgressIndicator = null
    }

    fun requestLatestReleasesInformation(): LatestReleaseInfo? {
        if (!pluginSettings().manageBinariesAutomatically) return null
        try {
            val result = createRequest(CliDownloader.LATEST_RELEASES_URL).readString()
            val response = "v" + result.removeSuffix("\n")
            latestReleaseInfo = LatestReleaseInfo(
                tagName = response,
                url = "https://static.snyk.io/cli/latest/${Platform.current().snykWrapperFileName}",
                name = response
            )
        } catch (ignore: Exception) {
        }

        return latestReleaseInfo
    }

    fun downloadLatestRelease(indicator: ProgressIndicator, project: Project) {
        currentProgressIndicator = indicator
        cliDownloadPublisher.cliDownloadStarted()
        indicator.isIndeterminate = true
        var succeeded = false
        val cliFile = getCliFile()
        try {
            val latestRelease = requestLatestReleasesInformation()

            if (latestRelease == null) {
                val failedMsg = "Failed to fetch the latest Snyk CLI release info. " +
                    "Please retry in a few minutes or contact support if the issue persists."
                errorHandler.showErrorWithRetryAndContactAction(failedMsg, project)
                return
            }
            val cliVersion = latestRelease.tagName

            indicator.text = "Downloading latest Snyk CLI release..."
            indicator.checkCanceled()

            val languageServerWrapper = LanguageServerWrapper.getInstance()
            try {
                if (languageServerWrapper.isInitialized) {
                    try {
                        languageServerWrapper.shutdown().get(2, TimeUnit.SECONDS)
                    } catch (e: RuntimeException) {
                        logger<SnykCliDownloaderService>()
                            .warn("Language server shutdown for download took too long, couldn't shutdown", e)
                    }
                }
                downloader.downloadFile(cliFile, downloader.expectedSha(), indicator)
                pluginSettings().cliVersion = cliVersionNumbers(cliVersion)
                pluginSettings().lastCheckDate = Date()
                succeeded = true
            } catch (e: HttpRequests.HttpStatusException) {
                errorHandler.handleHttpStatusException(e, project)
            } catch (e: IOException) {
                errorHandler.handleIOException(e, indicator, project)
            } catch (e: ChecksumVerificationException) {
                errorHandler.handleChecksumVerificationException(e, indicator, project)
            } finally {
                if (succeeded) languageServerWrapper.ensureLanguageServerInitialized() else stopCliDownload()
            }
        } finally {
            cliDownloadPublisher.cliDownloadFinished(succeeded)
            stopCliDownload()
        }
    }

    fun cliSilentAutoUpdate(indicator: ProgressIndicator, project: Project) {
        if (isFourDaysPassedSinceLastCheck()) {
            val latestReleaseInfo = requestLatestReleasesInformation()

            indicator.checkCanceled()

            val settings = pluginSettings()

            if (latestReleaseInfo?.tagName != null &&
                latestReleaseInfo.tagName.isNotEmpty() &&
                isNewVersionAvailable(settings.cliVersion, cliVersionNumbers(latestReleaseInfo.tagName))
            ) {
                downloadLatestRelease(indicator, project)

                settings.lastCheckDate = Date()
            }
        }
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

        tailrec fun checkIsNewVersionAvailable(
            currentCliVersionNumbers: List<String>,
            newCliVersionNumbers: List<String>
        ): Boolean {
            return if (currentCliVersionNumbers.isNotEmpty() && newCliVersionNumbers.isNotEmpty()) {
                val newVersionNumber = newCliVersionNumbers[0].toInt()
                val currentVersionNumber = currentCliVersionNumbers[0].toInt()

                when (val compareResult = newVersionNumber.compareTo(currentVersionNumber)) {
                    0 -> checkIsNewVersionAvailable(currentCliVersionNumbers.tail, newCliVersionNumbers.tail)
                    else -> compareResult > 0
                }
            } else {
                false
            }
        }

        return checkIsNewVersionAvailable(currentCliVersion!!.split('.'), newCliVersion!!.split('.'))
    }

    fun getLatestReleaseInfo(): LatestReleaseInfo? = this.latestReleaseInfo

    /**
     * Clear version number: v1.143.1 => 1.143.1

     * @param sourceVersion - source cli version string
     *
     * @return String
     */
    private fun cliVersionNumbers(sourceVersion: String): String = sourceVersion.substring(1, sourceVersion.length)
}

class LatestReleaseInfo(
    val url: String,
    val name: String,
    @SerializedName("tag_name") val tagName: String
)
