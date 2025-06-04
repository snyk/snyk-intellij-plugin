package io.snyk.plugin.cli

import com.intellij.credentialStore.Credentials
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyConfiguration.StaticProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.setupDummyCliFile
import snyk.PLUGIN_ID
import java.net.URLEncoder
import java.util.UUID


@Suppress("HttpUrlsUsage")
class ConsoleCommandRunnerTest : LightPlatformTestCase() {

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        setupDummyCliFile()
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
}
