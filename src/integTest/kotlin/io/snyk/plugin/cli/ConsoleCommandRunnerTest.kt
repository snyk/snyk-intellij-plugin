package io.snyk.plugin.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCli
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.services.SnykCliDownloaderService
import org.junit.Test
import snyk.PLUGIN_ID
import java.util.concurrent.TimeUnit

class ConsoleCommandRunnerTest : LightPlatformTestCase() {

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        getCli(project).setConsoleCommandRunner(null)
    }

    @Test
    fun testSetupCliEnvironmentVariables() {
        val generalCommandLine = GeneralCommandLine("")
        val snykPluginVersion = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "UNKNOWN"

        ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "test-api-token")

        assertEquals("test-api-token", generalCommandLine.environment["SNYK_TOKEN"])
        assertEquals("JETBRAINS_IDE", generalCommandLine.environment["SNYK_INTEGRATION_NAME"])
        assertEquals(snykPluginVersion, generalCommandLine.environment["SNYK_INTEGRATION_VERSION"])
        assertEquals("INTELLIJ IDEA IC", generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT"])
        assertEquals("2020.2", generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"])
    }

    @Test
    fun testCommandExecutionRequestWhileCliIsDownloading() {
        val cliFile = getCliFile()
        cliFile.delete()

        val progressManager = ProgressManager.getInstance() as CoreProgressManager
        val snykCliDownloaderService = service<SnykCliDownloaderService>()

        assertFalse("CLI binary should NOT exist at this stage", cliFile.exists())
        progressManager.runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "Test CLI download", true) {
                override fun run(indicator: ProgressIndicator) {
                    assertFalse("CLI binary should NOT exist at this stage", cliFile.exists())
                    snykCliDownloaderService.downloadLatestRelease(indicator)
                }
            },
            EmptyProgressIndicator()
        )

        assertFalse("CLI binary should NOT exist at this stage", cliFile.exists())
        val testRunFuture = progressManager.runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "Test CLI command invocation", true) {
                override fun run(indicator: ProgressIndicator) {
                    while (!cliFile.exists()) {
                        Thread.sleep(10) // lets wait till actual download begin
                    }
                    assertTrue(
                        "Downloading of CLI should be in progress at this stage.",
                        snykCliDownloaderService.isCliDownloading()
                    )
                    // No exception should happened while CLI is downloading and any CLI command is invoked
                    val commands = getCli(project).buildCliCommandsList(getApplicationSettingsStateService())
                    val output = ConsoleCommandRunner().execute(commands, getPluginPath(), "", project)
                    assertTrue(
                        "Should be NO output for CLI command while CLI is downloading, but received:\n$output",
                        output.isEmpty()
                    )
                }
            },
            EmptyProgressIndicator(),
            null
        )

        testRunFuture.get(5000, TimeUnit.MILLISECONDS)
    }
}
