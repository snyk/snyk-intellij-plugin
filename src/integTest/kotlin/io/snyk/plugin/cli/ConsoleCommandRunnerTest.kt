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
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightPlatformTestCase
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.sentry.protocol.SentryId
import io.snyk.plugin.DEFAULT_TIMEOUT_FOR_SCAN_WAITING_MS
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getOssService
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.download.SnykCliDownloaderService
import io.snyk.plugin.setupDummyCliFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.PLUGIN_ID
import snyk.errorHandler.SentryErrorReporter
import snyk.oss.OssService
import java.util.concurrent.TimeUnit

class ConsoleCommandRunnerTest : LightPlatformTestCase() {

    private val ossService: OssService
        get() = getOssService(project) ?: throw IllegalStateException("OSS service should be available")

    @Before
    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        setupDummyCliFile()
        // don't report to Sentry when running this test
        mockkObject(SentryErrorReporter)
        every { SentryErrorReporter.captureException(any()) } returns SentryId()
    }

    @After
    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
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

    @Suppress("SwallowedException")
    @Test
    fun testCommandExecutionRequestWhileCliIsDownloading() {
        val cliFile = getCliFile()
        cliFile.delete()

        val progressManager = ProgressManager.getInstance() as CoreProgressManager
        val snykCliDownloaderService = service<SnykCliDownloaderService>()
        var downloadIndicator: ProgressIndicator? = null

        assertFalse("CLI binary should NOT exist at this stage", cliFile.exists())
        progressManager.runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "Test CLI download", true) {
                override fun run(indicator: ProgressIndicator) {
                    assertFalse("CLI binary should NOT exist at this stage", cliFile.exists())
                    downloadIndicator = indicator
                    snykCliDownloaderService.downloadLatestRelease(indicator, project)
                }
            },
            EmptyProgressIndicator()
        )

        assertFalse("CLI binary should NOT exist at this stage", cliFile.exists())
        val testRunFuture = progressManager.runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "Test CLI command invocation", true) {
                override fun run(indicator: ProgressIndicator) {
                    while (!snykCliDownloaderService.isCliDownloading()) {
                        Thread.sleep(10) // lets wait till actual download begin
                    }
                    assertTrue(
                        "Downloading of CLI should be in progress at this stage.",
                        snykCliDownloaderService.isCliDownloading()
                    )
                    // CLINotExistsException should happen while CLI is not there,
                    // but downloading and any CLI command is invoked
                    try {
                        val commands = ossService.buildCliCommandsList_TEST_ONLY(listOf("test"))
                        ConsoleCommandRunner().execute(commands, getPluginPath(), "", project)
                        fail("Should have thrown CliNotExistsException, as the CLI is still downloading.")
                    } catch (e: CliNotExistsException) {
                        // this is expected and actually desired
                    }
                }
            },
            EmptyProgressIndicator(),
            null
        )

        testRunFuture.get(5000, TimeUnit.MILLISECONDS)
        // we have to stop CLI download process otherwise partially downloaded CLI file will be visible in other tests
        downloadIndicator?.cancel()
        while (snykCliDownloaderService.isCliDownloading()) {
            Thread.sleep(10) // lets wait till download actually stopped
        }
        assertFalse(cliFile.exists())
        verify(exactly = 0) { SentryErrorReporter.captureException(any()) }
    }

    @Test
    fun testErrorReportedWhenExecutionTimeoutExpire() {
        val registryValue = Registry.get("snyk.timeout.results.waiting")
        val defaultValue = registryValue.asInteger()
        assertEquals(DEFAULT_TIMEOUT_FOR_SCAN_WAITING_MS, defaultValue)
        registryValue.setValue(1)
        val seconds = "3"
        val sleepCommand = if (System.getProperty("os.name").toLowerCase().contains("win")) {
            listOf("timeout", seconds)
        } else {
            listOf("/bin/sleep", seconds)
        }

        val progressManager = ProgressManager.getInstance() as CoreProgressManager
        val testRunFuture = progressManager.runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "Test CLI command invocation", true) {
                override fun run(indicator: ProgressIndicator) {
                    val output = ConsoleCommandRunner().execute(sleepCommand, getPluginPath(), "", project)
                    assertTrue(
                        "Should get timeout error, but received:\n$output",
                        output.startsWith("Execution timeout")
                    )
                }
            },
            EmptyProgressIndicator(),
            null
        )
        testRunFuture.get(30000, TimeUnit.MILLISECONDS)

        verify(exactly = 1) { SentryErrorReporter.captureException(any()) }

        // clean up
        registryValue.setValue(DEFAULT_TIMEOUT_FOR_SCAN_WAITING_MS)
        getCliFile().delete()
    }
}
