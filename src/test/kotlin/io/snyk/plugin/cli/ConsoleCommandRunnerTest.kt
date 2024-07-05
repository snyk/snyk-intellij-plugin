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
import com.intellij.util.net.HttpConfigurable
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.sentry.protocol.SentryId
import io.snyk.plugin.DEFAULT_TIMEOUT_FOR_SCAN_WAITING_MS
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getOssService
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.download.SnykCliDownloaderService
import io.snyk.plugin.setupDummyCliFile
import snyk.PLUGIN_ID
import snyk.errorHandler.SentryErrorReporter
import snyk.oss.OssService
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

class ConsoleCommandRunnerTest : LightPlatformTestCase() {

    private val ossService: OssService
        get() = getOssService(project) ?: throw IllegalStateException("OSS service should be available")

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        setupDummyCliFile()
        // don't report to Sentry when running this test
        mockkObject(SentryErrorReporter)
        every { SentryErrorReporter.captureException(any()) } returns SentryId()
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
    }

    fun testSetupCliEnvironmentVariablesWithCustomEndpoint() {
        val oldEndpoint = pluginSettings().customEndpointUrl
        try {
            val generalCommandLine = GeneralCommandLine("")
            val expectedEndpoint = "https://customerTestEndpoint/v1"
            pluginSettings().customEndpointUrl = expectedEndpoint

            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals(expectedEndpoint, generalCommandLine.environment["SNYK_API"])
        } finally {
            pluginSettings().customEndpointUrl = oldEndpoint
        }
    }

    fun testSetupCliEnvironmentVariablesWithFedrampCustomEndpoint() {
        val oldEndpoint = pluginSettings().customEndpointUrl
        try {
            val generalCommandLine = GeneralCommandLine("")
            val expectedEndpoint = "https://api.fedramp.snykgov.io/"
            pluginSettings().customEndpointUrl = expectedEndpoint

            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals("1", generalCommandLine.environment["SNYK_CFG_DISABLE_ANALYTICS"])
        } finally {
            pluginSettings().customEndpointUrl = oldEndpoint
        }
    }

    fun testSetupCliEnvironmentVariablesWithOAuthEndpoint() {
        val oldEndpoint = pluginSettings().customEndpointUrl
        try {
            val generalCommandLine = GeneralCommandLine("")
            val expectedEndpoint = "https://api.xxx.snykgov.io/v1"
            generalCommandLine.environment["SNYK_TOKEN"] = "IntelliJ TEST"

            pluginSettings().customEndpointUrl = expectedEndpoint

            val token = """{ "access_token":"IntelliJ TEST"}"""
            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, token)

            assertEquals(expectedEndpoint, generalCommandLine.environment["SNYK_API"])
            assertEquals(null, generalCommandLine.environment["SNYK_TOKEN"])
            assertEquals(token, generalCommandLine.environment["INTERNAL_OAUTH_TOKEN_STORAGE"])
            assertEquals("1", generalCommandLine.environment["INTERNAL_SNYK_OAUTH_ENABLED"])
        } finally {
            pluginSettings().customEndpointUrl = oldEndpoint
        }
    }

    fun testSetupCliEnvironmentVariablesWithNonOAuthEndpoint() {
        val oldEndpoint = pluginSettings().customEndpointUrl
        try {
            val generalCommandLine = GeneralCommandLine("")
            val expectedEndpoint = "https://api.snyk.io/v1"
            generalCommandLine.environment["INTERNAL_OAUTH_TOKEN_STORAGE"] = "{}"

            pluginSettings().customEndpointUrl = expectedEndpoint
            pluginSettings().useTokenAuthentication = true

            val token = UUID.randomUUID().toString()
            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, token)

            assertEquals(expectedEndpoint, generalCommandLine.environment["SNYK_API"])
            assertEquals(token, generalCommandLine.environment["SNYK_TOKEN"])
            assertEquals(null, generalCommandLine.environment["INTERNAL_OAUTH_TOKEN_STORAGE"])
            assertEquals(null, generalCommandLine.environment["INTERNAL_SNYK_OAUTH_ENABLED"])
        } finally {
            pluginSettings().customEndpointUrl = oldEndpoint
        }
    }

    fun testSetupCliEnvironmentVariablesWithCustomEndpointNoTrailingSlash() {
        val oldEndpoint = pluginSettings().customEndpointUrl
        try {
            val generalCommandLine = GeneralCommandLine("")
            val expectedEndpoint = "https://customerTestEndpoint/v1"
            pluginSettings().customEndpointUrl = expectedEndpoint

            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals(expectedEndpoint, generalCommandLine.environment["SNYK_API"])
        } finally {
            pluginSettings().customEndpointUrl = oldEndpoint
        }
    }

    fun testSetupCliEnvironmentVariablesWithDisabledUsageAnalytics() {
        val originalUsageAnalyticsEnabled = pluginSettings().usageAnalyticsEnabled
        try {
            pluginSettings().usageAnalyticsEnabled = false
            val generalCommandLine = GeneralCommandLine("")
            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals("1", generalCommandLine.environment["SNYK_CFG_DISABLE_ANALYTICS"])
        } finally {
            pluginSettings().usageAnalyticsEnabled = originalUsageAnalyticsEnabled
        }
    }

    fun testSetupCliEnvironmentVariablesWithProxyWithoutAuth() {
        val httpConfigurable = HttpConfigurable.getInstance()
        val originalProxyHost = httpConfigurable.PROXY_HOST
        val originalProxyPort = httpConfigurable.PROXY_PORT
        val originalUseProxy = httpConfigurable.USE_HTTP_PROXY
        try {
            httpConfigurable.PROXY_PORT = 3128
            httpConfigurable.PROXY_HOST = "testProxy"
            httpConfigurable.USE_HTTP_PROXY = true

            val generalCommandLine = GeneralCommandLine("")
            val expectedProxy = "http://testProxy:3128"

            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals(expectedProxy, generalCommandLine.environment["https_proxy"])
        } finally {
            httpConfigurable.USE_HTTP_PROXY = originalUseProxy
            httpConfigurable.PROXY_HOST = originalProxyHost
            httpConfigurable.PROXY_PORT = originalProxyPort
        }
    }

    fun testSetupCliEnvironmentVariablesWithProxyWithAuth() {
        val httpConfigurable = HttpConfigurable.getInstance()
        val originalProxyHost = httpConfigurable.PROXY_HOST
        val originalProxyPort = httpConfigurable.PROXY_PORT
        val originalUseProxy = httpConfigurable.USE_HTTP_PROXY
        val originalProxyAuthentication = httpConfigurable.PROXY_AUTHENTICATION
        val originalLogin = httpConfigurable.proxyLogin
        val originalPassword = httpConfigurable.plainProxyPassword
        try {
            httpConfigurable.PROXY_PORT = 3128
            httpConfigurable.PROXY_HOST = "testProxy"
            httpConfigurable.USE_HTTP_PROXY = true
            httpConfigurable.PROXY_AUTHENTICATION = true
            httpConfigurable.proxyLogin = "testLogin"
            httpConfigurable.plainProxyPassword = "test%!@Password"
            val encodedLogin = URLEncoder.encode(httpConfigurable.proxyLogin, "UTF-8")
            val encodedPassword = URLEncoder.encode(httpConfigurable.plainProxyPassword, "UTF-8")

            val generalCommandLine = GeneralCommandLine("")
            val expectedProxy =
                "http://$encodedLogin:$encodedPassword@" +
                    "${httpConfigurable.PROXY_HOST}:${httpConfigurable.PROXY_PORT}"

            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals(expectedProxy, generalCommandLine.environment["https_proxy"])
        } finally {
            httpConfigurable.USE_HTTP_PROXY = originalUseProxy
            httpConfigurable.PROXY_HOST = originalProxyHost
            httpConfigurable.PROXY_PORT = originalProxyPort
            httpConfigurable.PROXY_AUTHENTICATION = originalProxyAuthentication
            httpConfigurable.proxyLogin = originalLogin
            httpConfigurable.plainProxyPassword = originalPassword
        }
    }

    fun testSetupCliEnvironmentVariablesWithProxyWithCancelledAuth() {
        val httpConfigurable = HttpConfigurable.getInstance()
        val originalProxyHost = httpConfigurable.PROXY_HOST
        val originalProxyPort = httpConfigurable.PROXY_PORT
        val originalUseProxy = httpConfigurable.USE_HTTP_PROXY
        val originalProxyAuthentication = httpConfigurable.PROXY_AUTHENTICATION
        val originalAuthenticationCancelled = httpConfigurable.AUTHENTICATION_CANCELLED
        val originalLogin = httpConfigurable.proxyLogin
        val originalPassword = httpConfigurable.plainProxyPassword
        try {
            httpConfigurable.PROXY_PORT = 3128
            httpConfigurable.PROXY_HOST = "testProxy"
            httpConfigurable.USE_HTTP_PROXY = true
            httpConfigurable.PROXY_AUTHENTICATION = true
            httpConfigurable.AUTHENTICATION_CANCELLED = true

            val generalCommandLine = GeneralCommandLine("")
            val expectedProxy =
                "http://${httpConfigurable.PROXY_HOST}:${httpConfigurable.PROXY_PORT}"

            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals(expectedProxy, generalCommandLine.environment["https_proxy"])
        } finally {
            httpConfigurable.USE_HTTP_PROXY = originalUseProxy
            httpConfigurable.PROXY_HOST = originalProxyHost
            httpConfigurable.PROXY_PORT = originalProxyPort
            httpConfigurable.PROXY_AUTHENTICATION = originalProxyAuthentication
            httpConfigurable.AUTHENTICATION_CANCELLED = originalAuthenticationCancelled
            httpConfigurable.proxyLogin = originalLogin
            httpConfigurable.plainProxyPassword = originalPassword
        }
    }

    fun testSetupCliEnvironmentVariables() {
        val generalCommandLine = GeneralCommandLine("")
        val snykPluginVersion = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: "UNKNOWN"
        pluginSettings().useTokenAuthentication = true
        ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "test-api-token")

        assertEquals("test-api-token", generalCommandLine.environment["SNYK_TOKEN"])
        assertEquals("JETBRAINS_IDE", generalCommandLine.environment["SNYK_INTEGRATION_NAME"])
        assertEquals(snykPluginVersion, generalCommandLine.environment["SNYK_INTEGRATION_VERSION"])
        assertEquals("INTELLIJ IDEA IC", generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT"])
        assertEquals("2023.1".length, generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"]?.length)
    }

    @Suppress("SwallowedException")
    fun testCommandExecutionRequestWhileCliIsDownloading() {
        val cliFile = getCliFile()
        cliFile.delete()
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { isCliInstalled() } returns false

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

        testRunFuture.get(30000, TimeUnit.MILLISECONDS)
        // we have to stop CLI download process otherwise partially downloaded CLI file will be visible in other tests
        downloadIndicator?.cancel()
        while (snykCliDownloaderService.isCliDownloading()) {
            Thread.sleep(10) // lets wait till download actually stopped
        }
        assertFalse(cliFile.exists())
        verify(exactly = 0) { SentryErrorReporter.captureException(any()) }
    }

    fun testErrorReportedWhenExecutionTimeoutExpire() {
        val registryValue = Registry.get("snyk.timeout.results.waiting")
        val defaultValue = registryValue.asInteger()
        assertEquals(DEFAULT_TIMEOUT_FOR_SCAN_WAITING_MS, defaultValue)
        registryValue.setValue(100)
        val seconds = "3"
        val sleepCommand = if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")) {
            // see https://www.ibm.com/support/pages/timeout-command-run-batch-job-exits-immediately-and-returns-error-input-redirection-not-supported-exiting-process-immediately
            listOf("ping", "-n", seconds, "localhost")
        } else {
            listOf("/bin/sleep", seconds)
        }

        val progressManager = ProgressManager.getInstance() as CoreProgressManager
        val testRunFuture = progressManager.runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "Test CLI command invocation", true) {
                override fun run(indicator: ProgressIndicator) {
                    val output = ConsoleCommandRunner().execute(sleepCommand, getPluginPath(), "", project)
                    assertTrue(
                        "Should get timeout error, but received:\n$output", output.startsWith("Execution timeout")
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
    }
}
