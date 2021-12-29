package io.snyk.plugin.services

import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test

class CliAdapterTest : LightPlatformTestCase() {

    private val dummyCliAdapter by lazy {
        object : CliAdapter<Any>(project) {
            override fun getErrorResult(errorMsg: String): Any = Unit
            override fun convertRawCliStringToCliResult(rawStr: String): Any = Unit
            override fun buildExtraOptions(): List<String> = emptyList()
        }
    }

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        resetSettings(project)
    }

    override fun tearDown() {
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
    }

    @Test
    fun testIsCliInstalledFailed() {
        removeDummyCliFile()

        val isCliInstalled = isCliInstalled()

        assertFalse(isCliInstalled)
    }

    @Test
    fun testIsCliInstalledSuccess() {
        setupDummyCliFile()

        val isCliInstalled = isCliInstalled()

        assertTrue(isCliInstalled)
    }

    @Test
    fun testBuildCliCommandsListWithCustomEndpointParameter() {
        setupDummyCliFile()

        pluginSettings().customEndpointUrl = "https://app.snyk.io/api"

        val defaultCommands = dummyCliAdapter.buildCliCommandsList_TEST_ONLY(listOf("fake_cli_command"))

        assertTrue(defaultCommands.contains("--API=https://app.snyk.io/api"))
    }

    @Test
    fun testBuildCliCommandsListWithInsecureParameter() {
        setupDummyCliFile()

        pluginSettings().ignoreUnknownCA = true

        val defaultCommands = dummyCliAdapter.buildCliCommandsList_TEST_ONLY(listOf("fake_cli_command"))

        assertTrue(defaultCommands.contains("--insecure"))
    }

    @Test
    fun testBuildCliCommandsListWithOrganizationParameter() {
        setupDummyCliFile()

        pluginSettings().organization = "test-org"

        val defaultCommands = dummyCliAdapter.buildCliCommandsList_TEST_ONLY(listOf("fake_cli_command"))

        assertTrue(defaultCommands.contains("--org=test-org"))
    }
}
