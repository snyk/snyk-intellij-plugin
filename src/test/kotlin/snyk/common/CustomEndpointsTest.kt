package snyk.common

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URI

class CustomEndpointsTest {
    lateinit var settings :SnykApplicationSettingsStateService
    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        settings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns settings
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `resolveCustomEndpoint returns production SaaS url if endpointUrl is null or empty`() {
        val endpointForNull = resolveCustomEndpoint(null)
        val endpointForEmpty = resolveCustomEndpoint("")

        assertEquals("https://api.snyk.io", endpointForNull)
        assertEquals("https://api.snyk.io", endpointForEmpty)
    }

    @Test
    fun `resolveCustomEndpoint converts app endpoint to api endpoint and saves it in settings`() {
        val endpointUrl = "https://app.snyk.io/api"
        val snykEndpointUrl = "https://snyk.io/api"
        val expected = "https://api.snyk.io"

        settings.customEndpointUrl = endpointUrl
        var actual = resolveCustomEndpoint(endpointUrl)

        assertEquals(expected, actual)
        assertEquals(expected, pluginSettings().customEndpointUrl)

        settings.customEndpointUrl = snykEndpointUrl
        actual = resolveCustomEndpoint(snykEndpointUrl)

        assertEquals(expected, actual)
        assertEquals(expected, pluginSettings().customEndpointUrl)

        settings.customEndpointUrl = ""
        actual = resolveCustomEndpoint("")

        assertEquals(expected, actual)
        assertEquals(expected, pluginSettings().customEndpointUrl)
    }


    @Test
    fun `resolveCustomEndpoint removes all trailing slashes if present`() {
        val endpointWithSingleTrailingSlash = resolveCustomEndpoint("https://app.on-prem.internal/api")
        val endpointWithMultipleTrailingSlashes = resolveCustomEndpoint("https://app.on-prem.internal/api///")

        assertEquals("https://api.on-prem.internal", endpointWithSingleTrailingSlash)
        assertEquals("https://api.on-prem.internal", endpointWithMultipleTrailingSlashes)
    }

    @Test
    fun `removeTrailingSlashesIfPresent do not return malformed(syntactically incorrect) url`() {
        val endpointWithNoHost = "http:/".removeTrailingSlashesIfPresent()

        assertEquals("http:/", endpointWithNoHost)
    }

    @Test
    fun `isSnykCodeAvailable returns true for SaaS deployments`() {
        val snykCodeAvailableForProduction = isSnykCodeAvailable("https://api.snyk.io")
        val snykCodeAvailableForDevelopment = isSnykCodeAvailable("https://dev.snyk.io/api")

        assertEquals(true, snykCodeAvailableForProduction)
        assertEquals(true, snykCodeAvailableForDevelopment)
    }

    @Test
    fun `isSnykCodeAvailable returns true if local engine is enabled`() {
        settings.localCodeEngineUrl = "https://api.foo.bar"
        settings.localCodeEngineEnabled = true

        assertEquals(isSnykCodeAvailable("https://api.foo.bar"), true)
    }

    @Test
    fun `isSnykCodeAvailable returns true for Single Tenant deployments`() {
        val snykCodeAvailable = isSnykCodeAvailable("https://api.random-uuid.snyk.io")

        assertEquals(true, snykCodeAvailable)
    }

    @Test
    fun `toSnykApiUrlV1 returns correct API URL`() {
        assertEquals("https://api.snyk.io/v1/", URI("https://snyk.io/api").toSnykAPIv1().toString())
        assertEquals("https://api.snyk.io/v1/", URI("https://app.snyk.io/api").toSnykAPIv1().toString())
        assertEquals("https://api.eu.snyk.io/v1/", URI("https://app.eu.snyk.io/api").toSnykAPIv1().toString())
        assertEquals("https://api.dev.snyk.io/v1/", URI("https://dev.snyk.io/api").toSnykAPIv1().toString())
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for SaaS deployments`() {
        val apiUrlForProduction = toSnykCodeApiUrl("https://api.snyk.io")
        val apiUrlForDevelopment = toSnykCodeApiUrl("https://dev.snyk.io/api")

        assertEquals("https://deeproxy.snyk.io/", apiUrlForProduction)
        assertEquals("https://deeproxy.dev.snyk.io/", apiUrlForDevelopment)
    }

    @Test
    fun `toSnykCodeApiUrl returns local engine URL if local engine is enabled`() {
        settings.localCodeEngineUrl = "https://api.foo.bar"
        settings.localCodeEngineEnabled = true
        val apiUrlForProduction = toSnykCodeApiUrl("https://api.snyk.io")

        assertEquals("https://api.foo.bar", apiUrlForProduction)
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for SaaS deployments using api url`() {
        val apiUrlForProduction = toSnykCodeApiUrl("https://api.eu.snyk.io")
        val apiUrlForDevelopment = toSnykCodeApiUrl("https://dev.api.eu.snyk.io")
        val apiUrlForWithV1 = toSnykCodeApiUrl("https://api.eu.snyk.io/v1")

        assertEquals("https://deeproxy.eu.snyk.io/", apiUrlForProduction)
        assertEquals("https://deeproxy.dev.eu.snyk.io/", apiUrlForDevelopment)
        assertEquals("https://deeproxy.eu.snyk.io/", apiUrlForWithV1)
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for Single Tenant deployments`() {
        val apiUrl = toSnykCodeApiUrl("https://api.random-uuid.snyk.io")

        assertEquals("https://deeproxy.random-uuid.snyk.io/", apiUrl)
    }

    @Test
    fun `toSnykCodeSettingsUrl returns correct settings url for SaaS deployments`() {
        assertEquals("https://app.snyk.io/manage/snyk-code", toSnykCodeSettingsUrl("https://api.snyk.io"))
        assertEquals("https://app.dev.snyk.io/manage/snyk-code", toSnykCodeSettingsUrl("https://dev.snyk.io"))
        assertEquals("https://app.dev.eu.snyk.io/manage/snyk-code", toSnykCodeSettingsUrl("https://dev.api.eu.snyk.io"))
        assertEquals("https://app.snyk.io/manage/snyk-code", toSnykCodeSettingsUrl("https://something.eu.snyk.io"))
    }

    @Test
    fun `toSnykCodeSettingsUrl returns APP-URL for local engine`() {
        settings.localCodeEngineUrl = "https://api.foo.bar"
        settings.localCodeEngineEnabled = true

        assertEquals("https://app.foo.bar", toSnykCodeSettingsUrl("https://api.foo.bar"))
    }

    @Test
    fun `toSnykCodeSettingsUrl returns correct settings url for Single Tenant deployments`() {
        val settingsUrl = toSnykCodeSettingsUrl("https://api.random-uuid.snyk.io")

        assertEquals("https://app.random-uuid.snyk.io/manage/snyk-code", settingsUrl)
    }

    @Test
    fun `isSnykAPI true when api subdomain and snyk domain`() {
        val uri = URI("https://api.snyk.io")
        assertTrue(uri.isSnykApi())
    }

    @Test
    fun `isSnykAPI false for api subdomain and not snyk domain`() {
        val uri = URI("https://api.notsnyk.io")
        assertFalse(uri.isSnykApi())
    }

    @Test
    fun `isSnykAPI false for non api path and snyk domain`() {
        val uri = URI("https://snyk.io/notapi")
        assertFalse(uri.isSnykApi())
    }

    @Test
    fun `non-api snyk domain does not need token`() {
        val uri = "https://snyk.io"
        assertFalse(needsSnykToken(uri))
    }

    @Test
    fun `non snyk domain does not need token`() {
        val uri = "https://NOTSNYK.io"
        assertFalse(needsSnykToken(uri))
    }

    @Test
    fun `api snyk path needs token`() {
        val uris = listOf(
            "https://api.snyk.io/v1",
            "https://app.eu.snyk.io/api",
            "https://app.au.snyk.io/api"
        )
        uris.forEach { uri ->
            assertTrue(needsSnykToken(uri))
        }
    }

    @Test
    fun `api snyk domain needs token`() {
        val uri = "https://api.snyk.io"
        assertTrue(needsSnykToken(uri))
    }

    @Test
    fun `needsSnykToken return true if local-engine is enabled`() {
        every { pluginSettings().localCodeEngineUrl } returns "https://foo.bar"
        every { pluginSettings().localCodeEngineEnabled } returns true
        val uri = "https://foo.bar/api"

        assertEquals(needsSnykToken(uri), true)
    }

    @Test
    fun `isAnalyticsPermitted false for URIs not allowed`() {
        val uris = listOf(
            "https://api.fedramp.snykgov.io",
            "https://api.eu.snyk.io",
            "https://api.au.snyk.io"
        )
        uris.forEach { uri ->
            assertFalse(URI(uri).isAnalyticsPermitted())
        }
    }

    @Test
    fun `isAnalyticsPermitted true for the right URIs`() {
        val uris = listOf(
            "https://snyk.io/api",
            "https://api.snyk.io",
            "https://api.us.snyk.io",
            "https://api.snyk.io",
            "https://api.snyk.io/v1"
        )

        uris.forEach { uri ->
            assertTrue(URI(uri).isAnalyticsPermitted())
        }
    }
}
