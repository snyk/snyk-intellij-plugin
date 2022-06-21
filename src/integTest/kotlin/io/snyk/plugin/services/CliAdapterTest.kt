package io.snyk.plugin.services

import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test
import snyk.common.SnykError

class CliAdapterTest : LightPlatformTestCase() {

    private val dummyCliAdapter by lazy {
        object : CliAdapter<Unit, DummyResults>(project) {
            override fun getProductResult(cliIssues: List<Unit>?, snykErrors: List<SnykError>) = DummyResults()
            override fun sanitizeCliIssues(cliIssues: Unit) = Unit
            override fun getCliIIssuesClass(): Class<Unit> = Unit::class.java
            override fun buildExtraOptions(): List<String> = emptyList()
        }
    }

    inner class DummyResults : CliResult<Unit>(null, emptyList()) {
        override val issuesCount: Int? = null
        override fun countBySeverity(severity: Severity): Int? = null
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
