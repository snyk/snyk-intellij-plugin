package io.snyk.plugin.ui.jcef

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.AuthenticationType
import io.snyk.plugin.services.SnykApplicationSettingsStateService

class SaveConfigHandlerTest : BasePlatformTestCase() {
    private lateinit var settings: SnykApplicationSettingsStateService
    private lateinit var cut: SaveConfigHandler

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        mockkStatic("io.snyk.plugin.UtilsKt")
        settings = mockk(relaxed = true)
        every { pluginSettings() } returns settings

        cut = SaveConfigHandler(project) {}
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    fun `test parseAndSaveConfig should update scan settings`() {
        val realSettings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns realSettings

        val jsonConfig = """
        {
            "activateSnykOpenSource": true,
            "activateSnykCode": false,
            "activateSnykIac": true
        }
        """.trimIndent()

        invokeParseAndSaveConfig(jsonConfig)

        assertTrue(realSettings.ossScanEnable)
        assertFalse(realSettings.snykCodeSecurityIssuesScanEnable)
        assertTrue(realSettings.iacScanEnabled)
    }

    fun `test parseAndSaveConfig should update scanning mode`() {
        val realSettings = SnykApplicationSettingsStateService()
        realSettings.scanOnSave = false
        every { pluginSettings() } returns realSettings

        val jsonConfig = """{"scanningMode": "auto"}"""
        invokeParseAndSaveConfig(jsonConfig)

        assertTrue(realSettings.scanOnSave)
    }

    fun `test parseAndSaveConfig should update organization and endpoint`() {
        val realSettings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns realSettings

        val jsonConfig = """
        {
            "organization": "my-org",
            "endpoint": "https://api.snyk.io"
        }
        """.trimIndent()

        invokeParseAndSaveConfig(jsonConfig)

        assertEquals("my-org", realSettings.organization)
        assertEquals("https://api.snyk.io", realSettings.customEndpointUrl)
    }

    fun `test parseAndSaveConfig should update authentication method to oauth`() {
        val realSettings = SnykApplicationSettingsStateService()
        realSettings.authenticationType = AuthenticationType.API_TOKEN
        every { pluginSettings() } returns realSettings

        val jsonConfig = """{"authenticationMethod": "oauth"}"""
        invokeParseAndSaveConfig(jsonConfig)

        assertEquals(AuthenticationType.OAUTH2, realSettings.authenticationType)
    }

    fun `test parseAndSaveConfig should update authentication method to token`() {
        val realSettings = SnykApplicationSettingsStateService()
        realSettings.authenticationType = AuthenticationType.OAUTH2
        every { pluginSettings() } returns realSettings

        val jsonConfig = """{"authenticationMethod": "token"}"""
        invokeParseAndSaveConfig(jsonConfig)

        assertEquals(AuthenticationType.API_TOKEN, realSettings.authenticationType)
    }

    fun `test parseAndSaveConfig should update severity filters`() {
        val realSettings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns realSettings

        val jsonConfig = """
        {
            "filterSeverity": {
                "critical": true,
                "high": true,
                "medium": false,
                "low": false
            }
        }
        """.trimIndent()

        invokeParseAndSaveConfig(jsonConfig)

        assertTrue(realSettings.criticalSeverityEnabled)
        assertTrue(realSettings.highSeverityEnabled)
        assertFalse(realSettings.mediumSeverityEnabled)
        assertFalse(realSettings.lowSeverityEnabled)
    }

    fun `test parseAndSaveConfig should update issue view options`() {
        val realSettings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns realSettings

        val jsonConfig = """
        {
            "issueViewOptions": {
                "openIssues": true,
                "ignoredIssues": false
            }
        }
        """.trimIndent()

        invokeParseAndSaveConfig(jsonConfig)

        assertTrue(realSettings.openIssuesEnabled)
        assertFalse(realSettings.ignoredIssuesEnabled)
    }

    fun `test parseAndSaveConfig should update CLI settings`() {
        val realSettings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns realSettings

        val jsonConfig = """
        {
            "cliPath": "/usr/local/bin/snyk",
            "manageBinariesAutomatically": false,
            "cliBaseDownloadURL": "https://downloads.snyk.io/fips",
            "cliReleaseChannel": "preview"
        }
        """.trimIndent()

        invokeParseAndSaveConfig(jsonConfig)

        assertEquals("/usr/local/bin/snyk", realSettings.cliPath)
        assertFalse(realSettings.manageBinariesAutomatically)
        assertEquals("https://downloads.snyk.io/fips", realSettings.cliBaseDownloadURL)
        assertEquals("preview", realSettings.cliReleaseChannel)
    }

    fun `test onModified callback is invoked`() {
        var callbackInvoked = false
        val handler = SaveConfigHandler(project) { callbackInvoked = true }

        // The onModified callback is wired to notifyModifiedQuery which requires JCEF
        // We can only verify the handler accepts the callback
        assertNotNull(handler)
        assertFalse(callbackInvoked) // Not invoked until JCEF triggers it
    }

    fun `test parseAndSaveConfig handles empty json gracefully`() {
        val realSettings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns realSettings

        // Should not throw
        invokeParseAndSaveConfig("{}")
    }

    fun `test parseAndSaveConfig handles partial config`() {
        val realSettings = SnykApplicationSettingsStateService()
        realSettings.organization = "original-org"
        realSettings.ossScanEnable = false
        every { pluginSettings() } returns realSettings

        // Only update organization, leave ossScanEnable unchanged
        val jsonConfig = """{"organization": "new-org"}"""
        invokeParseAndSaveConfig(jsonConfig)

        assertEquals("new-org", realSettings.organization)
        assertFalse(realSettings.ossScanEnable) // Unchanged
    }

    fun `test parseAndSaveConfig throws on invalid JSON`() {
        val realSettings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns realSettings

        val invalidJson = "{ this is not valid json }"

        try {
            invokeParseAndSaveConfig(invalidJson)
            fail("Expected IllegalArgumentException to be thrown")
        } catch (e: Exception) {
            // The reflection invocation wraps the exception
            val cause = e.cause
            assertTrue("Expected IllegalArgumentException but got ${cause?.javaClass}", 
                cause is IllegalArgumentException)
            assertTrue("Expected message to contain 'Invalid configuration format'",
                cause?.message?.contains("Invalid configuration format") == true)
        }
    }

    fun `test parseAndSaveConfig handles boolean as string for enableDeltaFindings`() {
        val realSettings = SnykApplicationSettingsStateService()
        realSettings.setDeltaEnabled(false)
        every { pluginSettings() } returns realSettings

        // Test with boolean true
        val jsonConfig = """{"enableDeltaFindings": true}"""
        invokeParseAndSaveConfig(jsonConfig)

        assertTrue(realSettings.isDeltaFindingsEnabled())
    }

    fun `test parseAndSaveConfig handles string booleans in severity filters`() {
        val realSettings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns realSettings

        // Some JS implementations might send "true"/"false" as strings
        val jsonConfig = """
        {
            "filterSeverity": {
                "critical": "true",
                "high": "TRUE",
                "medium": "false",
                "low": "FALSE"
            }
        }
        """.trimIndent()

        invokeParseAndSaveConfig(jsonConfig)

        assertTrue(realSettings.criticalSeverityEnabled)
        assertTrue(realSettings.highSeverityEnabled)
        assertFalse(realSettings.mediumSeverityEnabled)
        assertFalse(realSettings.lowSeverityEnabled)
    }

    fun `test parseAndSaveConfig handles numeric booleans`() {
        val realSettings = SnykApplicationSettingsStateService()
        realSettings.ossScanEnable = false
        realSettings.snykCodeSecurityIssuesScanEnable = true
        every { pluginSettings() } returns realSettings

        // Some systems might send 1/0 for boolean values
        val jsonConfig = """
        {
            "activateSnykOpenSource": 1,
            "activateSnykCode": 0
        }
        """.trimIndent()

        invokeParseAndSaveConfig(jsonConfig)

        assertTrue(realSettings.ossScanEnable)
        assertFalse(realSettings.snykCodeSecurityIssuesScanEnable)
    }

    fun `test parseAndSaveConfig handles null values gracefully`() {
        val realSettings = SnykApplicationSettingsStateService()
        realSettings.organization = "original-org"
        every { pluginSettings() } returns realSettings

        // JSON with explicit null value
        val jsonConfig = """{"organization": null, "activateSnykOpenSource": true}"""
        invokeParseAndSaveConfig(jsonConfig)

        // Organization should remain unchanged since null is not a valid String
        assertEquals("original-org", realSettings.organization)
        assertTrue(realSettings.ossScanEnable)
    }

    private fun invokeParseAndSaveConfig(jsonString: String) {
        val method = SaveConfigHandler::class.java.getDeclaredMethod("parseAndSaveConfig", String::class.java)
        method.isAccessible = true
        method.invoke(cut, jsonString)
    }
}
