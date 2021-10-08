package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.getCliFile
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test

class CliServiceTest : LightPlatformTestCase() {

    private val dummyCliService by lazy {
        object : CliService<Any>(project, listOf("fake_cli_command")) {
            override fun getErrorResult(errorMsg: String): Any = Unit
            override fun convertRawCliStringToCliResult(rawStr: String): Any = Unit
        }
    }

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        val settingsStateService = pluginSettings()

        settingsStateService.ignoreUnknownCA = false
        settingsStateService.usageAnalyticsEnabled = true
        settingsStateService.token = ""
        settingsStateService.customEndpointUrl = ""
        settingsStateService.cliVersion = ""
        settingsStateService.lastCheckDate = null
        settingsStateService.organization = ""

        project.service<SnykProjectSettingsStateService>().additionalParameters = ""
    }

    @Test
    fun testIsCliInstalledFailed() {
        val cliFile = getCliFile()

        if (cliFile.exists()) {
            assertTrue(cliFile.delete())
        }

        val isCliInstalled = dummyCliService.isCliInstalled()

        assertFalse(isCliInstalled)
    }

    @Test
    fun testIsCliInstalledSuccess() {
        setupDummyCliFile()

        val isCliInstalled = dummyCliService.isCliInstalled()

        assertTrue(isCliInstalled)
    }

    @Test
    fun testBuildCliCommandsListWithDefaults() {
        setupDummyCliFile()

        val defaultCommands = dummyCliService.buildCliCommandsList()

        assertEquals(getCliFile().absolutePath, defaultCommands[0])
        assertEquals("fake_cli_command", defaultCommands[1])
        assertEquals("--json", defaultCommands[2])
    }

    @Test
    fun testBuildCliCommandsListWithCustomEndpointParameter() {
        setupDummyCliFile()

        pluginSettings().customEndpointUrl = "https://app.snyk.io/api"

        val defaultCommands = dummyCliService.buildCliCommandsList()

        assertEquals("--API=https://app.snyk.io/api", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithInsecureParameter() {
        setupDummyCliFile()

        pluginSettings().ignoreUnknownCA = true

        val defaultCommands = dummyCliService.buildCliCommandsList()

        assertEquals("--insecure", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithOrganizationParameter() {
        setupDummyCliFile()

        pluginSettings().organization = "test-org"

        val defaultCommands = dummyCliService.buildCliCommandsList()

        assertEquals("--org=test-org", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithDisableAnalyticsParameter() {
        setupDummyCliFile()
        pluginSettings().usageAnalyticsEnabled = false

        val cliCommands = dummyCliService.buildCliCommandsList()

        assertEquals("--DISABLE_ANALYTICS", cliCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithFileParameter() {
        setupDummyCliFile()

        project.service<SnykProjectSettingsStateService>().additionalParameters = "--file=package.json"

        val defaultCommands = dummyCliService.buildCliCommandsList()

        assertEquals("--file=package.json", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithAllParameter() {
        setupDummyCliFile()

        val settingsStateService = pluginSettings()

        settingsStateService.token = "0000-1111-2222-3333"
        settingsStateService.customEndpointUrl = "https://app.snyk.io/api"
        settingsStateService.organization = "test-org"
        settingsStateService.ignoreUnknownCA = true

        project.service<SnykProjectSettingsStateService>().additionalParameters = "--file=package.json"

        val defaultCommands = dummyCliService.buildCliCommandsList()

        assertEquals("--API=https://app.snyk.io/api", defaultCommands[3])
        assertEquals("--insecure", defaultCommands[4])
        assertEquals("--org=test-org", defaultCommands[5])
        assertEquals("--file=package.json", defaultCommands[6])
    }

    @Test
    fun testBuildCliCommandsListWithMultiAdditionalParameters() {
        setupDummyCliFile()

        val settingsStateService = pluginSettings()

        settingsStateService.token = "0000-1111-2222-3333"
        settingsStateService.customEndpointUrl = "https://app.snyk.io/api"
        settingsStateService.organization = "test-org"
        settingsStateService.ignoreUnknownCA = true

        project.service<SnykProjectSettingsStateService>().additionalParameters =
            "--file=package.json --configuration-matching='iamaRegex' --sub-project=snyk"

        val defaultCommands = dummyCliService.buildCliCommandsList()

        assertEquals("--API=https://app.snyk.io/api", defaultCommands[3])
        assertEquals("--insecure", defaultCommands[4])
        assertEquals("--org=test-org", defaultCommands[5])
        assertEquals("--file=package.json", defaultCommands[6])
        assertEquals("--configuration-matching='iamaRegex'", defaultCommands[7])
        assertEquals("--sub-project=snyk", defaultCommands[8])
    }
}
