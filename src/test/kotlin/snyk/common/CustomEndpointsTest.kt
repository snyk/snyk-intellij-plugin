package snyk.common

import io.mockk.every
import io.mockk.mockk
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
    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
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

        assertEquals("https://app.snyk.io/api", endpointForNull)
        assertEquals("https://app.snyk.io/api", endpointForEmpty)
    }

    @Test
    fun `resolveCustomEndpoint removes all trailing slashes if present`() {
        val endpointWithSingleTrailingSlash = resolveCustomEndpoint("https://on-prem.internal/api")
        val endpointWithMultipleTrailingSlashes = resolveCustomEndpoint("https://on-prem.internal/api///")

        assertEquals("https://on-prem.internal/api", endpointWithSingleTrailingSlash)
        assertEquals("https://on-prem.internal/api", endpointWithMultipleTrailingSlashes)
    }

    @Test
    fun `removeTrailingSlashesIfPresent do not return malformed(syntactically incorrect) url`() {
        val endpointWithNoHost = "http:/".removeTrailingSlashesIfPresent()

        assertEquals("http:/", endpointWithNoHost)
    }

    @Test
    fun `isSnykCodeAvailable returns true for SaaS deployments`() {
        val snykCodeAvailableForProduction = isSnykCodeAvailable("https://snyk.io/api")
        val snykCodeAvailableForDevelopment = isSnykCodeAvailable("https://dev.snyk.io/api")

        assertEquals(true, snykCodeAvailableForProduction)
        assertEquals(true, snykCodeAvailableForDevelopment)
    }

    @Test
    fun `isSnykCodeAvailable returns true if local engine is enabled`() {
        every { pluginSettings().localCodeEngineUrl } returns "https://foo.bar/api"
        every { pluginSettings().localCodeEngineEnabled } returns true

        assertEquals(isSnykCodeAvailable("https://foo.bar/api"), true)
    }

    @Test
    fun `isSnykCodeAvailable returns true for Single Tenant deployments`() {
        val snykCodeAvailable = isSnykCodeAvailable("https://app.random-uuid.snyk.io/api")

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
        val apiUrlForProduction = toSnykCodeApiUrl("https://snyk.io/api")
        val apiUrlForDevelopment = toSnykCodeApiUrl("https://dev.snyk.io/api")

        assertEquals("https://deeproxy.snyk.io/", apiUrlForProduction)
        assertEquals("https://deeproxy.dev.snyk.io/", apiUrlForDevelopment)
    }

    @Test
    fun `toSnykCodeApiUrl returns local engine URL if local engine is enabled`() {
        every { pluginSettings().localCodeEngineUrl } returns "https://foo.bar/api"
        every { pluginSettings().localCodeEngineEnabled } returns true
        val apiUrlForProduction = toSnykCodeApiUrl("https://snyk.io/api")

        assertEquals("https://foo.bar/api", apiUrlForProduction)
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for SaaS deployments using api url`() {
        val apiUrlForProduction = toSnykCodeApiUrl("https://app.eu.snyk.io/api")
        val apiUrlForDevelopment = toSnykCodeApiUrl("https://dev.app.eu.snyk.io/api")

        assertEquals("https://deeproxy.eu.snyk.io/", apiUrlForProduction)
        assertEquals("https://deeproxy.dev.eu.snyk.io/", apiUrlForDevelopment)
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for Single Tenant deployments`() {
        val apiUrl = toSnykCodeApiUrl("https://app.random-uuid.snyk.io/api")

        assertEquals("https://deeproxy.random-uuid.snyk.io/", apiUrl)
    }

    @Test
    fun `toSnykCodeSettingsUrl returns correct settings url for SaaS deployments`() {
        assertEquals("https://app.snyk.io/manage/snyk-code", toSnykCodeSettingsUrl("https://snyk.io/api"))
        assertEquals("https://app.dev.snyk.io/manage/snyk-code", toSnykCodeSettingsUrl("https://dev.snyk.io/api"))
        assertEquals("https://app.dev.eu.snyk.io/manage/snyk-code", toSnykCodeSettingsUrl("https://dev.app.eu.snyk.io"))
        assertEquals("https://app.snyk.io/manage/snyk-code", toSnykCodeSettingsUrl("https://something.eu.snyk.io"))
    }

    @Test
    fun `toSnykCodeSettingsUrl returns URL unedited for local engine`() {
        every { pluginSettings().localCodeEngineUrl } returns "https://foo.bar/api"
        every { pluginSettings().localCodeEngineEnabled } returns true

        assertEquals(toSnykCodeSettingsUrl("https://foo.bar/api"), "https://foo.bar/api")
    }

    @Test
    fun `toSnykCodeSettingsUrl returns correct settings url for Single Tenant deployments`() {
        val settingsUrl = toSnykCodeSettingsUrl("https://app.random-uuid.snyk.io/api")

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
        var uri = "https://api.snyk.io/v1"
        assertTrue(needsSnykToken(uri))
        uri = "https://app.eu.snyk.io/api"
        assertTrue(needsSnykToken(uri))
        uri = "https://app.au.snyk.io/api"
        assertTrue(needsSnykToken(uri))
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
    fun `isOauth true for the right URI`() {
        val uri = URI("https://app.xxx.snykgov.io")
        assertTrue(uri.isOauth())
    }

    @Test
    fun `isAnalyticsPermitted false for URIs not allowed`() {
        var uri = URI("https://app.fedramp.snykgov.io")
        assertFalse(uri.isAnalyticsPermitted())
        uri = URI("https://app.eu.snyk.io/api")
        assertFalse(uri.isAnalyticsPermitted())
        uri = URI("https://app.au.snyk.io/api")
        assertFalse(uri.isAnalyticsPermitted())
    }

    @Test
    fun `isAnalyticsPermitted true for the right URIs`() {
        var uri = URI("https://app.snyk.io")
        assertTrue(uri.isAnalyticsPermitted())
        uri = URI("https://app.us.snyk.io")
        assertTrue(uri.isAnalyticsPermitted())
        uri = URI("https://app.snyk.io/api")
        assertTrue(uri.isAnalyticsPermitted())
        uri = URI("https://app.snyk.io/v1")
        assertTrue(uri.isAnalyticsPermitted())
    }
}
