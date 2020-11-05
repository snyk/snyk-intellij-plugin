package io.snyk.plugin.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.SnykPostStartupActivity
import io.snyk.plugin.getCli
import org.junit.Test

class ConsoleCommandRunnerTest : LightPlatformTestCase() {

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        getCli(project).setConsoleCommandRunner(null)
    }

    @Test
    fun testSetupCliEnvironmentVariables() {
        val generalCommandLine = GeneralCommandLine("")

        ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "test-api-token")

        val snykPluginVersion: String by lazy {
            val featureTrainerPluginId = PluginManagerCore.getPluginByClassName(SnykPostStartupActivity::class.java.name)
            PluginManagerCore.getPlugin(featureTrainerPluginId)?.version ?: "UNKNOWN"
        }

        assertEquals("test-api-token", generalCommandLine.environment["SNYK_TOKEN"])
        assertEquals("JETBRAINS_IDE", generalCommandLine.environment["SNYK_INTEGRATION_NAME"])
        assertEquals(snykPluginVersion, generalCommandLine.environment["SNYK_INTEGRATION_VERSION"])
        assertEquals("INTELLIJ IDEA IC", generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT"])
        assertEquals("2020.2", generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"])
    }
}
