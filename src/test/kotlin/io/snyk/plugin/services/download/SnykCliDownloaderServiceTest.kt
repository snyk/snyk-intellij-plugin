package io.snyk.plugin.services.download

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
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
}
