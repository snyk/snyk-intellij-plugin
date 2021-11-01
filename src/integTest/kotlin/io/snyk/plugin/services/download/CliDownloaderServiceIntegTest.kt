package io.snyk.plugin.services.download

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.io.HttpRequests
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.download.CliDownloader
import io.snyk.plugin.services.download.CliDownloaderErrorHandler
import io.snyk.plugin.services.download.SnykCliDownloaderService
import org.apache.http.HttpStatus
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.LocalDateTime

class CliDownloaderServiceIntegTest : LightPlatformTestCase() {

    private lateinit var indicator: EmptyProgressIndicator
    private lateinit var errorHandler: CliDownloaderErrorHandler
    private lateinit var downloader: CliDownloader
    private lateinit var cut: SnykCliDownloaderService
    private lateinit var cutSpy: SnykCliDownloaderService
    private val cliFile = getCliFile()

    @Before
    override fun setUp() {
        super.setUp()
        unmockkAll()
        cut = project.service()
        cutSpy = spyk(cut)
        errorHandler = mockk()
        downloader = spyk()
        cutSpy.downloader = downloader
        cutSpy.errorHandler = errorHandler
        indicator = EmptyProgressIndicator()
    }

    @Test
    fun testGetLatestReleasesInformation() {
        val latestReleaseInfo = project.service<SnykCliDownloaderService>().requestLatestReleasesInformation()

        assertNotNull(latestReleaseInfo)

        assertTrue(latestReleaseInfo!!.name.isNotEmpty())
        assertTrue(latestReleaseInfo.url.isNotEmpty())
        assertTrue(latestReleaseInfo.tagName.isNotEmpty())
    }

    @Test
    fun testDownloadLatestCliRelease() {
        ensureCliFileExistent()

        cutSpy.downloadLatestRelease(indicator, project)

        val downloadedFile = File(getPluginPath(), Platform.current().snykWrapperFileName)

        assertTrue(downloadedFile.exists())
        assertEquals(cutSpy.getLatestReleaseInfo()!!.tagName, "v" + pluginSettings().cliVersion)

        verify { downloader.downloadFile(cliFile, indicator) }
        verify { downloader.verifyCLIChecksum(cliFile) }
        downloadedFile.delete()
    }

    @Test
    fun testDownloadLatestCliReleaseFailsWhenShaDoesNotMatch() {
        ensureCliFileExistent()
        every { downloader.calculateSha256(any()) } returns "wrong-sha"
        justRun { errorHandler.handleChecksumVerificationException(any(), any(), any()) }

        cutSpy.downloadLatestRelease(indicator, project)

        verify(exactly = 1) { downloader.verifyCLIChecksum(cliFile) }
        verify(exactly = 1) { errorHandler.handleChecksumVerificationException(any(), any(), any()) }
    }

    private fun ensureCliFileExistent() {
        if (!cliFile.exists()) {
            cliFile.createNewFile()
        }
    }

    @Test
    fun testDownloadLatestCliReleaseShouldHandleSocketTimeout() {
        val indicator = EmptyProgressIndicator()
        val exceptionMessage = "Read Timed Out"
        val ioException = SocketTimeoutException(exceptionMessage)

        every { downloader.downloadFile(any(), any()) } throws ioException
        justRun { errorHandler.handleIOException(ioException, indicator, project) }

        cutSpy.downloadLatestRelease(indicator, project)

        verify {
            downloader.downloadFile(any(), any())
            errorHandler.handleIOException(ioException, indicator, project)
        }
    }

    @Test
    fun testDownloadLatestCliReleaseShouldHandleHttpStatusException() {
        val httpStatusException = HttpRequests.HttpStatusException("status bad", HttpStatus.SC_GATEWAY_TIMEOUT, "url")

        every { downloader.downloadFile(any(), any()) } throws httpStatusException
        justRun { errorHandler.handleHttpStatusException(httpStatusException, project) }

        cutSpy.downloadLatestRelease(indicator, project)

        verify {
            downloader.downloadFile(any(), any())
            errorHandler.handleHttpStatusException(httpStatusException, project)
        }
    }

    @Test
    fun testDownloadLatestCliReleaseWhenNoReleaseInfoAvailable() {
        val cliDownloaderService = project.service<SnykCliDownloaderService>()

        val cliDownloaderServiceSpy = spyk(cliDownloaderService)
        every { cliDownloaderServiceSpy.requestLatestReleasesInformation() } returns null

        assertNoThrowable {
            cliDownloaderServiceSpy.downloadLatestRelease(EmptyProgressIndicator(), project)
        }
    }

    @Test
    fun testCliSilentAutoUpdate() {
        val currentDate = LocalDateTime.now()

        pluginSettings().cliVersion = "1.342.2"
        pluginSettings().setLastCheckDate(currentDate.minusDays(5))

        val cliDownloaderService = project.service<SnykCliDownloaderService>()

        val cliFile = getCliFile()

        if (!cliFile.exists()) {
            cliFile.createNewFile()
        }

        cliDownloaderService.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

        assertTrue(getCliFile().exists())
        assertEquals(currentDate.toLocalDate(), pluginSettings().getLastCheckDate())
        assertEquals(
            cliDownloaderService.getLatestReleaseInfo()!!.tagName,
            "v" + pluginSettings().cliVersion
        )

        cliFile.delete()
    }

    @Test
    fun testCliSilentAutoUpdateWhenPreviousUpdateInfoIsNull() {
        val currentDate = LocalDate.now()

        val applicationSettingsStateService = pluginSettings()

        applicationSettingsStateService.cliVersion = ""
        applicationSettingsStateService.lastCheckDate = null

        val cliDownloaderService = project.service<SnykCliDownloaderService>()

        val cliFile = getCliFile()

        if (!cliFile.exists()) {
            cliFile.createNewFile()
        }

        cliDownloaderService.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

        assertTrue(getCliFile().exists())

        assertEquals(currentDate, applicationSettingsStateService.getLastCheckDate())
        assertEquals(
            cliDownloaderService.getLatestReleaseInfo()!!.tagName,
            "v" + applicationSettingsStateService.cliVersion
        )

        cliFile.delete()
    }

    @Test
    fun testIsNewVersionAvailable() {
        pluginSettings().lastCheckDate = null

        val cliDownloaderService = project.service<SnykCliDownloaderService>()

        assertTrue(cliDownloaderService.isNewVersionAvailable("1.342.2", "1.345.1"))
        assertTrue(cliDownloaderService.isNewVersionAvailable("1.342.2", "2.345.1"))
        assertTrue(cliDownloaderService.isNewVersionAvailable("1.345.2", "2.342.9"))

        assertFalse(cliDownloaderService.isNewVersionAvailable("2.342.2", "1.342.1"))
        assertFalse(cliDownloaderService.isNewVersionAvailable("1.343.1", "1.342.2"))
        assertFalse(cliDownloaderService.isNewVersionAvailable("1.342.2", "1.342.1"))

        assertFalse(cliDownloaderService.isNewVersionAvailable("1.342.2", "1.342.2"))
    }

    @Test
    fun testCheckIsFourDaysPassedSinceLastCheck() {
        val todayDate = LocalDateTime.now()
        val lastCheckDate = todayDate.minusDays(4)

        pluginSettings().setLastCheckDate(lastCheckDate)

        assertTrue(project.service<SnykCliDownloaderService>().isFourDaysPassedSinceLastCheck())
    }
}
