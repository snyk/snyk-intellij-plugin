package io.snyk.plugin.services.download

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.HttpRequests
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getCliFile
import io.snyk.plugin.mockCliDownload
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import java.io.File
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import org.apache.http.HttpStatus
import org.junit.Assert.assertNotEquals
import snyk.common.lsp.LanguageServerWrapper

class SnykCliDownloaderServiceIntegTest : LightPlatformTestCase() {

  private lateinit var indicator: EmptyProgressIndicator
  private lateinit var errorHandler: CliDownloaderErrorHandler
  private lateinit var downloader: CliDownloader
  private lateinit var cut: SnykCliDownloaderService
  private lateinit var cutSpy: SnykCliDownloaderService
  private lateinit var cliFile: File

  override fun setUp() {
    super.setUp()
    unmockkAll()
    resetSettings(project)
    mockkStatic("io.snyk.plugin.UtilsKt")

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(project) } returns mockk(relaxed = true)

    val settings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns settings
    settings.currentLSProtocolVersion = settings.requiredLsProtocolVersion
    settings.cliReleaseChannel = "preview"

    cliFile = getCliFile()

    cut = project.service()
    cutSpy = spyk(cut)
    errorHandler = mockk()
    downloader = spyk()
    cutSpy.downloader = downloader
    cutSpy.errorHandler = errorHandler

    indicator = EmptyProgressIndicator()
    removeDummyCliFile()
  }

  override fun tearDown() {
    unmockkAll()
    resetSettings(project)
    removeDummyCliFile()
    super.tearDown()
  }

  /**
   * Should be THE ONLY test where we actually do download the CLI Do __MOCK__ cli download in ANY
   * other test to reduce testing time needed !!! This test fails when the preview release is not
   * available yet
   */
  fun testDownloadLatestCliRelease() {
    val releaseInfo = cutSpy.requestLatestReleasesInformation()
    if (releaseInfo.isNullOrEmpty()) {
      println("Skipping testDownloadLatestCliRelease: preview release not available")
      return
    }

    ensureCliFileExistent()

    cutSpy.downloadLatestRelease(indicator, project)

    assertTrue(cliFile.exists())
    verify { downloader.downloadFile(cliFile, any(), indicator) }
    verify { downloader.verifyChecksum(any(), any()) }
  }

  fun testDownloadLatestCliReleaseFailsWhenShaDoesNotMatch() {
    ensureCliFileExistent()

    mockCliDownload()

    every { downloader.calculateSha256(any()) } returns "wrong-sha"

    justRun { errorHandler.handleChecksumVerificationException(any(), any(), any(), any()) }
    // this is needed, but I don't know why the config is not picked up from setUp()
    every { pluginSettings() } returns SnykApplicationSettingsStateService()

    cutSpy.downloadLatestRelease(indicator, project)

    verify(exactly = 1) { downloader.verifyChecksum(any(), any()) }
    verify(exactly = 1) {
      errorHandler.handleChecksumVerificationException(any(), any(), any(), any())
    }
  }

  private fun ensureCliFileExistent() {
    if (!cliFile.exists()) {
      cliFile.createNewFile()
    }
  }

  fun testDownloadLatestCliReleaseShouldHandleSocketTimeout() {
    mockCliDownload()
    val indicator = EmptyProgressIndicator()
    val exceptionMessage = "Read Timed Out"
    val ioException = SocketTimeoutException(exceptionMessage)
    // mocked version response is 1.2.3
    val cliVersion = "1.2.3"
    every { downloader.downloadFile(any(), any(), any()) } throws ioException
    justRun { errorHandler.handleIOException(ioException, cliVersion, indicator, project) }

    cutSpy.downloadLatestRelease(indicator, project)

    verify {
      downloader.downloadFile(any(), any(), any())
      errorHandler.handleIOException(ioException, cliVersion, indicator, project)
    }
  }

  fun testDownloadLatestCliReleaseShouldHandleHttpStatusException() {
    val httpStatusException =
      HttpRequests.HttpStatusException("status bad", HttpStatus.SC_GATEWAY_TIMEOUT, "url")

    every { cutSpy.requestLatestReleasesInformation() } returns "1.1294.0"
    every { downloader.downloadFile(any(), any(), any()) } throws httpStatusException
    justRun { errorHandler.handleHttpStatusException(httpStatusException, project) }

    cutSpy.downloadLatestRelease(indicator, project)

    verify {
      downloader.downloadFile(any(), any(), any())
      errorHandler.handleHttpStatusException(httpStatusException, project)
    }
  }

  fun testDownloadLatestCliReleaseWhenNoReleaseInfoAvailable() {
    val cliDownloaderService = project.service<SnykCliDownloaderService>()

    val cliDownloaderServiceSpy = spyk(cliDownloaderService)
    every { cliDownloaderServiceSpy.requestLatestReleasesInformation() } returns null

    assertNoThrowable {
      cliDownloaderServiceSpy.downloadLatestRelease(EmptyProgressIndicator(), project)
    }
  }

  fun testCliSilentAutoUpdate_protocolVersionNotEqual_moreThan4Days() {
    val currentDate = LocalDateTime.now()

    pluginSettings().currentLSProtocolVersion = pluginSettings().requiredLsProtocolVersion - 1
    pluginSettings().cliVersion = "1.2.2"
    pluginSettings().setLastCheckDate(currentDate.minusDays(5))

    ensureCliFileExistent()
    mockCliDownload()

    every { downloader.downloadFile(any(), any(), any()) } returns cliFile

    cutSpy.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

    assertTrue(getCliFile().exists())
    // this checks if the update date is now changed
    assertEquals(currentDate.toLocalDate(), pluginSettings().getLastCheckDate())
  }

  fun testCliSilentAutoUpdate_protocolVersionNotEqual_lessThan4Days() {
    val currentDate = LocalDateTime.now()

    pluginSettings().currentLSProtocolVersion = pluginSettings().requiredLsProtocolVersion - 1
    pluginSettings().cliVersion = "1.2.2"
    pluginSettings().setLastCheckDate(currentDate.minusDays(1))

    ensureCliFileExistent()
    mockCliDownload()

    every { downloader.downloadFile(any(), any(), any()) } returns cliFile

    cutSpy.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

    assertTrue(getCliFile().exists())
    // this checks if the update date is now changed
    assertEquals(currentDate.toLocalDate(), pluginSettings().getLastCheckDate())
  }

  fun testCliSilentAutoUpdate_protocolVersionEqual_lessThan4Days() {
    val currentDate = LocalDateTime.now()

    pluginSettings().cliVersion = "1.2.2"
    pluginSettings().setLastCheckDate(currentDate.minusDays(1))

    ensureCliFileExistent()
    mockCliDownload()

    every { downloader.downloadFile(any(), any(), any()) } returns cliFile

    cutSpy.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

    assertTrue(getCliFile().exists())
    assertNotEquals(currentDate.toLocalDate(), pluginSettings().getLastCheckDate())
  }

  fun testCliSilentAutoUpdate_protocolVersionEqual_moreThan4Days() {
    val currentDate = LocalDateTime.now()

    pluginSettings().cliVersion = "1.2.2"
    pluginSettings().setLastCheckDate(currentDate.minusDays(5))

    ensureCliFileExistent()
    mockCliDownload()

    every { downloader.downloadFile(any(), any(), any()) } returns cliFile

    cutSpy.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

    assertTrue(getCliFile().exists())
    assertEquals(currentDate.toLocalDate(), pluginSettings().getLastCheckDate())
  }

  fun testCliSilentAutoUpdateWhenPreviousUpdateInfoIsNull() {
    val currentDate = LocalDate.now()
    val settings = pluginSettings()
    settings.lastCheckDate = null
    ensureCliFileExistent()
    every { cutSpy.requestLatestReleasesInformation() } returns "testTag"
    justRun { cutSpy.downloadLatestRelease(any(), any()) }

    cutSpy.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

    assertEquals(currentDate, settings.getLastCheckDate())
    verify { cutSpy.downloadLatestRelease(any(), any()) }
  }

  fun testCliSilentAutoUpdateWhenForced() {
    val currentDate = LocalDate.now()
    val settings = pluginSettings()
    settings.lastCheckDate = Date()
    ensureCliFileExistent()
    every { cutSpy.requestLatestReleasesInformation() } returns "testTag"
    justRun { cutSpy.downloadLatestRelease(any(), any()) }

    cutSpy.cliSilentAutoUpdate(EmptyProgressIndicator(), project, force = true)

    assertEquals(currentDate, settings.getLastCheckDate())
    verify { cutSpy.downloadLatestRelease(any(), any()) }
  }

  fun testIsNewVersionAvailable() {
    pluginSettings().lastCheckDate = null

    val cliDownloaderService = project.service<SnykCliDownloaderService>()

    assertTrue(cliDownloaderService.isNewVersionAvailable("1.342.2", "1.345.1"))
    assertTrue(cliDownloaderService.isNewVersionAvailable("1.342.2", "2.345.1"))
    assertTrue(cliDownloaderService.isNewVersionAvailable("1.345.2", "2.342.9"))

    assertFalse(cliDownloaderService.isNewVersionAvailable("1.342.2", "1.342.2"))
  }

  fun testCheckIsFourDaysPassedSinceLastCheck() {
    val todayDate = LocalDateTime.now()
    val lastCheckDate = todayDate.minusDays(4)

    pluginSettings().setLastCheckDate(lastCheckDate)

    assertTrue(project.service<SnykCliDownloaderService>().isFourDaysPassedSinceLastCheck())
  }
}
