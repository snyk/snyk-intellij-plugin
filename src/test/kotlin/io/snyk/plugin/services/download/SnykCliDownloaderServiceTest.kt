package io.snyk.plugin.services.download

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import io.mockk.verify
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SnykCliDownloaderServiceTest {
    private val settingsStateService: SnykApplicationSettingsStateService = mockk()

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { pluginSettings() } returns settingsStateService
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `requestLatestReleasesInformation should returns null if updates disabled`() {
        val cut = SnykCliDownloaderService()
        every { settingsStateService.manageBinariesAutomatically } returns false

        assertNull(cut.requestLatestReleasesInformation())

        verify { settingsStateService.manageBinariesAutomatically }
        confirmVerified(settingsStateService)
    }

    @Test
    fun `downloadLatestRelease should return without doing anything if updates disabled`() {
        val cut = SnykCliDownloaderService()
        every { settingsStateService.manageBinariesAutomatically } returns false
        every { settingsStateService.cliPath } returns "dummyPath"
        every { isCliInstalled() } returns false
        mockkObject(SnykBalloonNotificationHelper)
        justRun { SnykBalloonNotificationHelper.showError(any(), any()) }

        cut.downloadLatestRelease(mockk(), mockk())

        verify { settingsStateService.manageBinariesAutomatically }
        verify { settingsStateService.cliPath }
        verify(exactly = 1) {
            SnykBalloonNotificationHelper.showError(
                any(), any()
            )
        }
        confirmVerified(settingsStateService) // this makes sure, no publisher, no cli path, nothing is used
        confirmVerified(SnykBalloonNotificationHelper)
        unmockkObject(SnykBalloonNotificationHelper)
    }
}
