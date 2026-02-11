package io.snyk.plugin.services.download

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.intellij.util.io.HttpRequests
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsyncApp
import io.snyk.plugin.services.download.HttpRequestHelper.createRequest
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date

@Suppress("MemberVisibilityCanBePrivate")
@Service(Service.Level.APP)
class SnykCliDownloaderService {

  companion object {
    const val NUMBER_OF_DAYS_BETWEEN_RELEASE_CHECK = 4
  }

  var downloader = CliDownloader()
  var errorHandler = CliDownloaderErrorHandler()

  private val logger = logger<SnykCliDownloaderService>()

  private var currentProgressIndicator: ProgressIndicator? = null

  fun isCliDownloading() = currentProgressIndicator != null && !application.isDisposed

  fun stopCliDownload() =
    currentProgressIndicator?.let {
      it.cancel()
      currentProgressIndicator = null
    }

  fun requestLatestReleasesInformation(): String? {
    if (!pluginSettings().manageBinariesAutomatically) return null
    return try {
      createRequest(CliDownloader.LATEST_RELEASES_URL).readString()
    } catch (ignore: Exception) {
      logger.warn(ignore)
      null
    }
  }

  fun downloadLatestRelease(indicator: ProgressIndicator, project: Project) {
    currentProgressIndicator = indicator
    logger.debug("CLI download starting")
    publishAsyncApp(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC) { cliDownloadStarted() }
    indicator.isIndeterminate = true
    var succeeded = false
    val cliFile = getCliFile()
    logger.debug("Starting CLI download to: ${cliFile.absolutePath}")
    val latestRelease: String
    try {
      latestRelease = requestLatestReleasesInformation() ?: ""

      if (latestRelease.isEmpty()) {
        val failedMsg =
          "Failed to fetch the latest Snyk CLI release info. " +
            "Please retry in a few minutes or contact support if the issue persists."
        logger.warn("Failed to fetch release info, URL: ${CliDownloader.LATEST_RELEASES_URL}")
        errorHandler.showErrorWithRetryAndContactAction(failedMsg, project)
        return
      }

      logger.debug("Downloading CLI version: $latestRelease")
      indicator.text = "Downloading latest Snyk CLI release..."
      indicator.checkCanceled()

      try {
        downloader.downloadFile(cliFile, latestRelease, indicator)
        pluginSettings().cliVersion = latestRelease
        pluginSettings().lastCheckDate = Date()
        succeeded = true
        logger.debug(
          "CLI download succeeded: ${cliFile.absolutePath}, exists=${cliFile.exists()}, canExecute=${cliFile.canExecute()}"
        )
      } catch (e: HttpRequests.HttpStatusException) {
        logger.warn("HTTP error during download", e)
        errorHandler.handleHttpStatusException(e, project)
      } catch (e: IOException) {
        logger.warn("IO error during download", e)
        errorHandler.handleIOException(e, latestRelease, indicator, project)
      } catch (e: ChecksumVerificationException) {
        logger.warn("Checksum verification failed", e)
        errorHandler.handleChecksumVerificationException(e, latestRelease, indicator, project)
      }
    } finally {
      logger.debug("CLI download finished, succeeded=$succeeded")
      publishAsyncApp(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC) { cliDownloadFinished(succeeded) }
      if (succeeded) {
        publishAsyncApp(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC) { restartCLI() }
      }
      stopCliDownload()
    }
  }

  /**
   * Check if the CLI version is outdated and download the latest release if needed.
   *
   * Scenarios:
   * 1. No CLI installed -> download
   * 2. CLI installed and current LS protocol version matches required version
   *     - check if 4 days passed since last check
   *     - if yes -> download
   *     - if no -> do nothing
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

    return ChronoUnit.DAYS.between(previousDate, LocalDate.now()) >=
      NUMBER_OF_DAYS_BETWEEN_RELEASE_CHECK
  }

  fun isNewVersionAvailable(currentCliVersion: String?, newCliVersion: String?): Boolean {
    val cliVersionsNullOrEmpty =
      currentCliVersion == null ||
        newCliVersion == null ||
        currentCliVersion.isEmpty() ||
        newCliVersion.isEmpty()

    if (cliVersionsNullOrEmpty) return true

    return currentCliVersion != newCliVersion
  }

  /**
   * Verifies that the installed CLI matches the stored checksum. If the checksum doesn't match (CLI
   * was modified/corrupted), shows a notification offering to redownload.
   *
   * @param project - current project for notification and download
   * @return true if CLI is valid or no checksum stored, false if checksum mismatch detected
   */
  fun verifyCliIntegrity(project: Project): Boolean {
    if (!pluginSettings().manageBinariesAutomatically) return true

    val settings = pluginSettings()
    val storedSha256 = settings.cliSha256
    if (storedSha256.isNullOrBlank()) {
      // No stored checksum - trigger download to ensure we have a verified CLI
      // The checksum will be stored after successful download
      logger<SnykCliDownloaderService>()
        .debug("No stored CLI checksum, triggering download for verification")
      return false
    }

    val cliFile = getCliFile()
    if (!cliFile.exists()) {
      // CLI doesn't exist, return false to trigger download
      return false
    }

    return try {
      val currentSha256 = downloader.calculateSha256(cliFile.readBytes())
      if (currentSha256.equals(storedSha256, ignoreCase = true)) {
        true
      } else {
        logger<SnykCliDownloaderService>()
          .warn("CLI integrity check failed. Expected: $storedSha256, Found: $currentSha256")
        showCliIntegrityWarning(project)
        false
      }
    } catch (e: Exception) {
      logger<SnykCliDownloaderService>().warn("Failed to verify CLI integrity: ${e.message}")
      true // Don't block on verification errors
    }
  }

  private fun showCliIntegrityWarning(project: Project) {
    val redownloadAction =
      object : AnAction("Redownload CLI") {
        override fun actionPerformed(e: AnActionEvent) {
          getSnykTaskQueueService(project)?.downloadLatestRelease(force = true)
        }
      }

    val ignoreAction =
      object : AnAction("Ignore") {
        override fun actionPerformed(e: AnActionEvent) {
          // Update stored checksum to current file to stop warning
          val cliFile = getCliFile()
          if (cliFile.exists()) {
            try {
              pluginSettings().cliSha256 = downloader.calculateSha256(cliFile.readBytes())
            } catch (_: Exception) {
              // Ignore
            }
          }
        }
      }

    SnykBalloonNotificationHelper.showWarn(
      "Snyk CLI integrity check failed. The CLI binary may have been modified or corrupted.",
      project,
      redownloadAction,
      ignoreAction,
    )
  }
}
