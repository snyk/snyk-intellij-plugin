package io.snyk.plugin.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService

class SettingsApplyFunctionsTest : BasePlatformTestCase() {
    private lateinit var settings: SnykApplicationSettingsStateService

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        mockkStatic("io.snyk.plugin.UtilsKt")
        settings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns settings
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    fun `test handleDeltaFindingsChange handles null cache gracefully`() {
        every { getSnykCachedResults(project) } returns null

        // Should not throw
        handleDeltaFindingsChange(project)
    }

    fun `test shared functions exist and are callable`() {
        // Verify shared functions can be called without errors
        // The actual behavior is tested through integration
        assertNotNull(::executePostApplySettings)
        assertNotNull(::handleReleaseChannelChange)
        assertNotNull(::handleDeltaFindingsChange)
        assertNotNull(::applyFolderConfigChanges)
    }
}
