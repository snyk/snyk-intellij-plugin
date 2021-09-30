package io.snyk.plugin.services

import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.getApplicationSettingsStateService
import org.junit.Test

class SnykApplicationSettingsStateServiceTest : LightPlatformTestCase() {

    @Test
    fun testContainerAndIacEnablementDependency() {
        val settings = getApplicationSettingsStateService()

        // initial default values test
        assertFalse(settings.iacScanEnabled)
        assertFalse(settings.containerScanEnabled)

        settings.containerScanEnabled = true
        assertFalse("Container scan should remain disabled if IaC scan is disabled", settings.containerScanEnabled)

        settings.iacScanEnabled = true
        assertTrue("Container scan could be enabled if IaC scan is enabled", settings.containerScanEnabled)
    }
}
