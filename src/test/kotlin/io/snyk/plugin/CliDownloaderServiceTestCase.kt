package io.snyk.plugin

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.cli.CliDownloaderService
import io.snyk.plugin.cli.Platform
import org.junit.Test
import java.io.File

class CliDownloaderServiceTestCase : LightPlatformTestCase() {

    @Test
    fun testGetLatestReleasesInformation() {
        val latestReleaseInfo = project.service<CliDownloaderService>().requestLatestReleasesInformation()

        assertNotNull(latestReleaseInfo)

        assertTrue(latestReleaseInfo!!.id > 0)
        assertTrue(latestReleaseInfo.name.isNotEmpty())
        assertTrue(latestReleaseInfo.url.isNotEmpty())
        assertTrue(latestReleaseInfo.tagName.isNotEmpty())
    }

    @Test
    fun testDownloadLatestCliRelease() {
        val cliDownloaderService = project.service<CliDownloaderService>()

        val cliFile = cliDownloaderService.getCliFile()

        if (!cliFile.exists()) {
            cliFile.createNewFile()
        }

        project.service<CliDownloaderService>().downloadLatestRelease()

        val downloadedFile = File(getPluginPath(), Platform.current().snykWrapperFileName)

        assertTrue(downloadedFile.exists())
        assertEquals(cliDownloaderService.getLatestReleaseInfo()!!.tagName,
            "v" + getApplicationSettingsStateService().getCliVersion())

        downloadedFile.delete()
    }

    private fun resetSettings() {
        getApplicationSettingsStateService().setCliVersion("")
        getApplicationSettingsStateService().setLastCheckDate(null)
    }
}
