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
import io.snyk.plugin.getCliFile
import io.snyk.plugin.mockCliDownload
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import org.apache.http.HttpStatus
import org.junit.Test
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

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
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

    @Test
    fun testGetLatestReleasesInformation() {
        val latestReleaseInfo = project.service<SnykCliDownloaderService>().requestLatestReleasesInformation()

        assertNotNull(latestReleaseInfo)

        assertTrue(latestReleaseInfo!!.name.isNotEmpty())
        assertTrue(latestReleaseInfo.url.isNotEmpty())
        assertTrue(latestReleaseInfo.tagName.isNotEmpty())
    }

    /**
     * Should be THE ONLY test where we actually do download the CLI
     * !!! Do __MOCK__ cli download in ANY other test to reduce testing time needed !!!
     */
    @Test
    fun testDownloadLatestCliRelease() {
        ensureCliFileExistent()

        cutSpy.downloadLatestRelease(indicator, project)

        val downloadedFile = cliFile

        assertTrue(downloadedFile.exists())
        assertEquals(cutSpy.getLatestReleaseInfo()!!.tagName, "v" + pluginSettings().cliVersion)

        verify { downloader.downloadFile(cliFile, any(), indicator) }
        verify { downloader.verifyChecksum(any(), any()) }
    }

    @Test
    fun testDownloadLatestCliReleaseFailsWhenShaDoesNotMatch() {
        ensureCliFileExistent()

        mockCliDownload()

        every { downloader.calculateSha256(any()) } returns "wrong-sha"
        justRun { errorHandler.handleChecksumVerificationException(any(), any(), any()) }

        cutSpy.downloadLatestRelease(indicator, project)

        verify(exactly = 1) { downloader.verifyChecksum(any(), any()) }
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

        every { downloader.downloadFile(any(), any(), any()) } throws ioException
        justRun { errorHandler.handleIOException(ioException, indicator, project) }

        cutSpy.downloadLatestRelease(indicator, project)

        verify {
            downloader.downloadFile(any(), any(), any())
            errorHandler.handleIOException(ioException, indicator, project)
        }
    }

    @Test
    fun testDownloadLatestCliReleaseShouldHandleHttpStatusException() {
        val httpStatusException = HttpRequests.HttpStatusException("status bad", HttpStatus.SC_GATEWAY_TIMEOUT, "url")

        every { downloader.downloadFile(any(), any(), any()) } throws httpStatusException
        justRun { errorHandler.handleHttpStatusException(httpStatusException, project) }

        cutSpy.downloadLatestRelease(indicator, project)

        verify {
            downloader.downloadFile(any(), any(), any())
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

        ensureCliFileExistent()

        every { downloader.downloadFile(any(), any(), any()) } returns cliFile

        cutSpy.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

        assertTrue(getCliFile().exists())
        assertEquals(currentDate.toLocalDate(), pluginSettings().getLastCheckDate())
        assertEquals(
            cutSpy.getLatestReleaseInfo()!!.tagName,
            "v" + pluginSettings().cliVersion
        )
    }

    @Test
    fun testCliSilentAutoUpdateWhenPreviousUpdateInfoIsNull() {
        val currentDate = LocalDate.now()

        val settings = pluginSettings()

        settings.cliVersion = ""
        settings.lastCheckDate = null

        ensureCliFileExistent()

        every { downloader.downloadFile(any(), any(), any()) } returns cliFile

        cutSpy.cliSilentAutoUpdate(EmptyProgressIndicator(), project)

        assertTrue(getCliFile().exists())

        assertEquals(currentDate, settings.getLastCheckDate())
        assertEquals(
            cutSpy.getLatestReleaseInfo()!!.tagName,
            "v" + settings.cliVersion
        )
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
