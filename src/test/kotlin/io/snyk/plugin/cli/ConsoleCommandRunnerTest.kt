package io.snyk.plugin.cli

import com.intellij.credentialStore.Credentials
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyConfiguration.StaticProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.sentry.protocol.SentryId
import io.snyk.plugin.DEFAULT_TIMEOUT_FOR_SCAN_WAITING_MS
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.setupDummyCliFile
import snyk.PLUGIN_ID
import snyk.errorHandler.SentryErrorReporter
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit


@Suppress("HttpUrlsUsage")
class ConsoleCommandRunnerTest : LightPlatformTestCase() {

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
            val expectedEndpoint = "https://customerTestEndpoint"
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
            val expectedEndpoint = "https://api.xxx.snykgov.io"
            generalCommandLine.environment["SNYK_TOKEN"] = "IntelliJ TEST"

            pluginSettings().customEndpointUrl = expectedEndpoint
            pluginSettings().useTokenAuthentication = false

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
            val expectedEndpoint = "https://api.snyk.io"
            generalCommandLine.environment["INTERNAL_OAUTH_TOKEN_STORAGE"] = "{}"

            pluginSettings().customEndpointUrl = expectedEndpoint
            pluginSettings().useTokenAuthentication = true

            val token = UUID.randomUUID().toString()
            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, token)

            assertEquals(expectedEndpoint, generalCommandLine.environment["SNYK_API"])
            assertEquals(token, generalCommandLine.environment["SNYK_TOKEN"])
            assertEquals(null, generalCommandLine.environment["INTERNAL_OAUTH_TOKEN_STORAGE"])
            assertEquals("0", generalCommandLine.environment["INTERNAL_SNYK_OAUTH_ENABLED"])
        } finally {
            pluginSettings().customEndpointUrl = oldEndpoint
        }
    }

    fun testSetupCliEnvironmentVariablesWithCustomEndpointNoTrailingSlash() {
        val oldEndpoint = pluginSettings().customEndpointUrl
        try {
            val generalCommandLine = GeneralCommandLine("")
            val expectedEndpoint = "https://customerTestEndpoint"
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

    @Suppress("UnstableApiUsage")
    fun testSetupCliEnvironmentVariablesWithProxyWithoutAuth() {
        val proxySettings = ProxySettings.getInstance()
        val origConfig = proxySettings.getProxyConfiguration()
        try {
            proxySettings.setProxyConfiguration(createDummyProxyConfig())

            val generalCommandLine = GeneralCommandLine("")
            val expectedProxy = "http://testProxy:3128"

            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals(expectedProxy, generalCommandLine.environment["https_proxy"])
        } finally {
            proxySettings.setProxyConfiguration(origConfig)
        }
    }

    @Suppress("UnstableApiUsage")
    fun testSetupCliEnvironmentVariablesWithProxyWithAuth() {
        val proxySettings = ProxySettings.getInstance()
        val credentialStore = ProxyCredentialStore.getInstance()
        val origConfig = proxySettings.getProxyConfiguration()
        val password = "test%!@Password"
        val user = "testLogin"
        try {
            val proxyConfiguration = createDummyProxyConfig()
            proxySettings.setProxyConfiguration(proxyConfiguration)

            val credentials = Credentials(user, password)
            credentialStore.setCredentials(proxyConfiguration.host, proxyConfiguration.port, credentials, false)

            val encodedLogin = URLEncoder.encode(user, "UTF-8")
            val encodedPassword = URLEncoder.encode(password, "UTF-8")

            val generalCommandLine = GeneralCommandLine("")
            val expectedProxy =
                "http://$encodedLogin:$encodedPassword@" +
                    "${proxyConfiguration.host}:${proxyConfiguration.port}"

            ConsoleCommandRunner().setupCliEnvironmentVariables(generalCommandLine, "")

            assertEquals(expectedProxy, generalCommandLine.environment["https_proxy"])
        } finally {
            proxySettings.setProxyConfiguration(origConfig)
            credentialStore.clearAllCredentials()
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

private fun createDummyProxyConfig(): StaticProxyConfiguration {
    val newConfig = object : StaticProxyConfiguration {
        override val exceptions: String
            get() = "testExceptions"
        override val host: String
            get() = "testProxy"
        override val port: Int
            get() = 3128
        override val protocol: ProxyConfiguration.ProxyProtocol
            get() = ProxyConfiguration.ProxyProtocol.HTTP
    }
    return newConfig
}
