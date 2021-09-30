package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getPluginPath
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class SnykCliDownloaderServiceTest : LightPlatformTestCase() {

    @Test
    fun testGetLatestReleasesInformation() {
        val latestReleaseInfo = project.service<SnykCliDownloaderService>().requestLatestReleasesInformation()

        assertNotNull(latestReleaseInfo)

        assertTrue(latestReleaseInfo!!.id > 0)
        assertTrue(latestReleaseInfo.name.isNotEmpty())
        assertTrue(latestReleaseInfo.url.isNotEmpty())
        assertTrue(latestReleaseInfo.tagName.isNotEmpty())
    }

    @Test
    fun testDownloadLatestCliRelease() {
        val cliDownloaderService = project.service<SnykCliDownloaderService>()

        val cliFile = getCliFile()

        if (!cliFile.exists()) {
            cliFile.createNewFile()
        }

        project.service<SnykCliDownloaderService>().downloadLatestRelease(EmptyProgressIndicator(), project)

        val downloadedFile = File(getPluginPath(), Platform.current().snykWrapperFileName)

        assertTrue(downloadedFile.exists())
        assertEquals(
            cliDownloaderService.getLatestReleaseInfo()!!.tagName,
            "v" + pluginSettings().cliVersion
        )

        downloadedFile.delete()
    }

    @Test
    fun testDownloadLatestCliReleaseWhenNoReleaseInfoAvailable() {
        val cliDownloaderService = project.service<SnykCliDownloaderService>()

        val cliDownloaderServiceSpy = Mockito.spy(cliDownloaderService)
        Mockito.doReturn(null).`when`<SnykCliDownloaderService>(cliDownloaderServiceSpy).requestLatestReleasesInformation()

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
