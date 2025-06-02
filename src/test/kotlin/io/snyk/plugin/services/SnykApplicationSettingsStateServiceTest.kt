package io.snyk.plugin.services

import io.snyk.plugin.Severity
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date
import java.util.UUID

class SnykApplicationSettingsStateServiceTest {

    @Test
    fun hasSeverityEnabled_allSeveritiesEnabledByDefault() {
        val target = SnykApplicationSettingsStateService()

        assertTrue(target.hasSeverityEnabled(Severity.CRITICAL))
        assertTrue(target.hasSeverityEnabled(Severity.HIGH))
        assertTrue(target.hasSeverityEnabled(Severity.MEDIUM))
        assertTrue(target.hasSeverityEnabled(Severity.LOW))
    }

    @Test
    fun hasSeverityEnabled_someDisabled() {
        val target = SnykApplicationSettingsStateService()
        target.lowSeverityEnabled = false
        target.criticalSeverityEnabled = false

        assertFalse(target.hasSeverityEnabled(Severity.CRITICAL))
        assertFalse(target.hasSeverityEnabled(Severity.LOW))
    }

    @Test
    fun `reset should restore all settings to default values`() {
        val settingsToModify = SnykApplicationSettingsStateService()

        // Modify some properties to non-default values
        settingsToModify.pluginInstalledSent = true
        settingsToModify.useTokenAuthentication = true
        settingsToModify.currentLSProtocolVersion = 10
        settingsToModify.autofixEnabled = true
        settingsToModify.isGlobalIgnoresFeatureEnabled = true
        settingsToModify.cliBaseDownloadURL = "http://localhost/custom"
        settingsToModify.cliPath = "/custom/path/snyk"
        settingsToModify.cliReleaseChannel = "preview"
        settingsToModify.issuesToDisplay = SnykApplicationSettingsStateService.DISPLAY_NEW_ISSUES
        settingsToModify.manageBinariesAutomatically = false
        settingsToModify.fileListenerEnabled = false
        settingsToModify.token = "test-token"
        settingsToModify.customEndpointUrl = "https://my.custom.snyk/"
        settingsToModify.organization = "test-org"
        settingsToModify.ignoreUnknownCA = true
        settingsToModify.cliVersion = "1.2.3"
        settingsToModify.scanOnSave = false
        @Suppress("DEPRECATION")
        settingsToModify.cliScanEnable = false // This will be reset to true
        settingsToModify.ossScanEnable = false
        settingsToModify.snykCodeSecurityIssuesScanEnable = false
        settingsToModify.snykCodeQualityIssuesScanEnable = false
        settingsToModify.iacScanEnabled = false
        settingsToModify.containerScanEnabled = false
        settingsToModify.sastOnServerEnabled = true
        settingsToModify.sastSettingsError = true
        settingsToModify.localCodeEngineEnabled = true
        settingsToModify.localCodeEngineUrl = "http://localhost:1234"
        settingsToModify.usageAnalyticsEnabled = false
        settingsToModify.crashReportingEnabled = false
        settingsToModify.lowSeverityEnabled = false
        settingsToModify.mediumSeverityEnabled = false
        settingsToModify.highSeverityEnabled = false
        settingsToModify.criticalSeverityEnabled = false
        settingsToModify.openIssuesEnabled = false
        settingsToModify.ignoredIssuesEnabled = true
        settingsToModify.treeFiltering.ossResults = false
        settingsToModify.treeFiltering.criticalSeverity = false
        settingsToModify.lastCheckDate = Date()
        settingsToModify.pluginFirstRun = false
        val oldUserAnonymousId = settingsToModify.userAnonymousId
        val oldLastTimeFeedbackRequestShown = settingsToModify.lastTimeFeedbackRequestShown
        settingsToModify.showFeedbackRequest = false

        // Call reset
        settingsToModify.reset()

        // Create a fresh instance for comparison (represents default state)
        val defaultSettings = SnykApplicationSettingsStateService()

        // Assertions for all properties
        assertEquals(defaultSettings.pluginInstalledSent, settingsToModify.pluginInstalledSent)
        assertEquals(defaultSettings.useTokenAuthentication, settingsToModify.useTokenAuthentication)
        assertEquals(defaultSettings.currentLSProtocolVersion, settingsToModify.currentLSProtocolVersion)
        assertEquals(defaultSettings.autofixEnabled, settingsToModify.autofixEnabled)
        assertEquals(defaultSettings.isGlobalIgnoresFeatureEnabled, settingsToModify.isGlobalIgnoresFeatureEnabled)
        assertEquals(defaultSettings.cliBaseDownloadURL, settingsToModify.cliBaseDownloadURL)
        assertEquals(defaultSettings.cliPath, settingsToModify.cliPath) // Depends on getPluginPath(), should be same
        assertEquals(defaultSettings.cliReleaseChannel, settingsToModify.cliReleaseChannel)
        assertEquals(defaultSettings.issuesToDisplay, settingsToModify.issuesToDisplay)
        assertEquals(defaultSettings.manageBinariesAutomatically, settingsToModify.manageBinariesAutomatically)
        assertEquals(defaultSettings.fileListenerEnabled, settingsToModify.fileListenerEnabled)
        assertNull(settingsToModify.token) // token is nullable, default is null
        assertNull(settingsToModify.customEndpointUrl) // nullable, default is null
        assertNull(settingsToModify.organization) // nullable, default is null
        assertEquals(defaultSettings.ignoreUnknownCA, settingsToModify.ignoreUnknownCA)
        assertNull(settingsToModify.cliVersion) // nullable, default is null
        assertEquals(defaultSettings.scanOnSave, settingsToModify.scanOnSave)

        @Suppress("DEPRECATION")
        assertEquals(defaultSettings.cliScanEnable, settingsToModify.cliScanEnable)

        assertEquals(defaultSettings.ossScanEnable, settingsToModify.ossScanEnable)
        assertEquals(defaultSettings.snykCodeSecurityIssuesScanEnable, settingsToModify.snykCodeSecurityIssuesScanEnable)
        assertEquals(defaultSettings.snykCodeQualityIssuesScanEnable, settingsToModify.snykCodeQualityIssuesScanEnable)
        assertEquals(defaultSettings.iacScanEnabled, settingsToModify.iacScanEnabled)
        assertEquals(defaultSettings.containerScanEnabled, settingsToModify.containerScanEnabled)

        assertEquals(defaultSettings.sastOnServerEnabled, settingsToModify.sastOnServerEnabled)
        assertEquals(defaultSettings.sastSettingsError, settingsToModify.sastSettingsError)
        assertEquals(defaultSettings.localCodeEngineEnabled, settingsToModify.localCodeEngineEnabled)
        assertEquals(defaultSettings.localCodeEngineUrl, settingsToModify.localCodeEngineUrl)
        assertEquals(defaultSettings.usageAnalyticsEnabled, settingsToModify.usageAnalyticsEnabled)
        assertEquals(defaultSettings.crashReportingEnabled, settingsToModify.crashReportingEnabled)

        assertEquals(defaultSettings.lowSeverityEnabled, settingsToModify.lowSeverityEnabled)
        assertEquals(defaultSettings.mediumSeverityEnabled, settingsToModify.mediumSeverityEnabled)
        assertEquals(defaultSettings.highSeverityEnabled, settingsToModify.highSeverityEnabled)
        assertEquals(defaultSettings.criticalSeverityEnabled, settingsToModify.criticalSeverityEnabled)

        assertEquals(defaultSettings.openIssuesEnabled, settingsToModify.openIssuesEnabled)
        assertEquals(defaultSettings.ignoredIssuesEnabled, settingsToModify.ignoredIssuesEnabled)

        // Assert TreeFiltering properties (comparing field by field as TreeFiltering doesn't override equals)
        assertEquals(defaultSettings.treeFiltering.ossResults, settingsToModify.treeFiltering.ossResults)
        assertEquals(defaultSettings.treeFiltering.codeSecurityResults, settingsToModify.treeFiltering.codeSecurityResults)
        assertEquals(defaultSettings.treeFiltering.codeQualityResults, settingsToModify.treeFiltering.codeQualityResults)
        assertEquals(defaultSettings.treeFiltering.iacResults, settingsToModify.treeFiltering.iacResults)
        assertEquals(defaultSettings.treeFiltering.containerResults, settingsToModify.treeFiltering.containerResults)
        assertEquals(defaultSettings.treeFiltering.criticalSeverity, settingsToModify.treeFiltering.criticalSeverity)
        assertEquals(defaultSettings.treeFiltering.highSeverity, settingsToModify.treeFiltering.highSeverity)
        assertEquals(defaultSettings.treeFiltering.mediumSeverity, settingsToModify.treeFiltering.mediumSeverity)
        assertEquals(defaultSettings.treeFiltering.lowSeverity, settingsToModify.treeFiltering.lowSeverity)
        assertEquals(defaultSettings.treeFiltering.ossResults, settingsToModify.treeFiltering.ossResults)
        assertEquals(defaultSettings.treeFiltering.codeSecurityResults, settingsToModify.treeFiltering.codeSecurityResults)
        assertEquals(defaultSettings.treeFiltering.iacResults, settingsToModify.treeFiltering.iacResults)

        assertNull(settingsToModify.lastCheckDate) // default is null
        assertEquals(defaultSettings.pluginFirstRun, settingsToModify.pluginFirstRun)

        // For properties that get new unique values on reset
        assertNotNull(settingsToModify.lastTimeFeedbackRequestShown)
        assertNotEquals(oldLastTimeFeedbackRequestShown, settingsToModify.lastTimeFeedbackRequestShown)
        // Check it's a recent date (e.g., not older than defaultSettings's, allowing for slight time diff)
        assertTrue(settingsToModify.lastTimeFeedbackRequestShown.time >= defaultSettings.lastTimeFeedbackRequestShown.time - 5000) // within 5s

        assertEquals(defaultSettings.showFeedbackRequest, settingsToModify.showFeedbackRequest)

        assertNotNull(settingsToModify.userAnonymousId)
        assertNotEquals(oldUserAnonymousId, settingsToModify.userAnonymousId)
        try {
            UUID.fromString(settingsToModify.userAnonymousId) // Check if it's a valid UUID
        } catch (e: IllegalArgumentException) {
            org.junit.Assert.fail("userAnonymousId is not a valid UUID after reset")
        }
    }
}
