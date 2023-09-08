package snyk.common

import io.mockk.mockk
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.assertEquals
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.Before
import java.net.URI
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.junit.After

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

        assertThat(endpointForNull, equalTo("https://app.snyk.io/api"))
        assertThat(endpointForEmpty, equalTo("https://app.snyk.io/api"))
    }

    @Test
    fun `resolveCustomEndpoint removes all trailing slashes if present`() {
        val endpointWithSingleTrailingSlash = resolveCustomEndpoint("https://on-prem.internal/api")
        val endpointWithMultipleTrailingSlashes = resolveCustomEndpoint("https://on-prem.internal/api///")

        assertThat(endpointWithSingleTrailingSlash, equalTo("https://on-prem.internal/api"))
        assertThat(endpointWithMultipleTrailingSlashes, equalTo("https://on-prem.internal/api"))
    }

    @Test
    fun `removeTrailingSlashesIfPresent do not return malformed(syntactically incorrect) url`() {
        val endpointWithNoHost = "http:/".removeTrailingSlashesIfPresent()

        assertThat(endpointWithNoHost, equalTo("http:/"))
    }

    @Test
    fun `isSnykCodeAvailable returns true for SaaS deployments`() {
        val snykCodeAvailableForProduction = isSnykCodeAvailable("https://snyk.io/api")
        val snykCodeAvailableForDevelopment = isSnykCodeAvailable("https://dev.snyk.io/api")

        assertThat(snykCodeAvailableForProduction, equalTo(true))
        assertThat(snykCodeAvailableForDevelopment, equalTo(true))
    }

    @Test
    fun `isSnykCodeAvailable returns true if local engine is enabled`() {
        every { pluginSettings().localCodeEngineUrl } returns "http://foo.bar/api"
        every { pluginSettings().localCodeEngineEnabled } returns true

        assertEquals(isSnykCodeAvailable("https://foo.bar/api"), true)
    }

    @Test
    fun `isSnykCodeAvailable returns true for Single Tenant deployments`() {
        val snykCodeAvailable = isSnykCodeAvailable("https://app.random-uuid.snyk.io/api")

        assertThat(snykCodeAvailable, equalTo(true))
    }

    @Test
    fun `toSnykApiUrlV1 returns correct API URL`() {
        assertThat(URI("https://snyk.io/api").toSnykAPIv1().toString(), equalTo("https://api.snyk.io/v1/"))
        assertThat(URI("https://app.snyk.io/api").toSnykAPIv1().toString(), equalTo("https://api.snyk.io/v1/"))
        assertThat(URI("https://app.eu.snyk.io/api").toSnykAPIv1().toString(), equalTo("https://api.eu.snyk.io/v1/"))
        assertThat(URI("https://dev.snyk.io/api").toSnykAPIv1().toString(), equalTo("https://api.dev.snyk.io/v1/"))
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for SaaS deployments`() {
        val apiUrlForProduction = toSnykCodeApiUrl("https://snyk.io/api")
        val apiUrlForDevelopment = toSnykCodeApiUrl("https://dev.snyk.io/api")

        assertThat(apiUrlForProduction, equalTo("https://deeproxy.snyk.io/"))
        assertThat(apiUrlForDevelopment, equalTo("https://deeproxy.dev.snyk.io/"))
    }

    @Test
    fun `toSnykCodeApiUrl returns local engine URL if local engine is enabled`() {
        every { pluginSettings().localCodeEngineUrl } returns "http://foo.bar/api"
        every { pluginSettings().localCodeEngineEnabled } returns true
        val apiUrlForProduction = toSnykCodeApiUrl("https://snyk.io/api")

        assertEquals(apiUrlForProduction, "http://foo.bar/api")
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for SaaS deployments using api url`() {
        val apiUrlForProduction = toSnykCodeApiUrl("https://app.eu.snyk.io/api")
        val apiUrlForDevelopment = toSnykCodeApiUrl("https://dev.app.eu.snyk.io/api")

        assertThat(apiUrlForProduction, equalTo("https://deeproxy.eu.snyk.io/"))
        assertThat(apiUrlForDevelopment, equalTo("https://deeproxy.dev.eu.snyk.io/"))
    }

    @Test
    fun `toSnykCodeApiUrl returns correct deeproxy url for Single Tenant deployments`() {
        val apiUrl = toSnykCodeApiUrl("https://app.random-uuid.snyk.io/api")

        assertThat(apiUrl, equalTo("https://deeproxy.random-uuid.snyk.io/"))
    }

    @Test
    fun `toSnykCodeSettingsUrl returns correct settings url for SaaS deployments`() {
        assertThat(toSnykCodeSettingsUrl("https://snyk.io/api"), equalTo("https://app.snyk.io/manage/snyk-code"))
        assertThat(
            toSnykCodeSettingsUrl("https://dev.snyk.io/api"), equalTo("https://app.dev.snyk.io/manage/snyk-code")
        )
        assertThat(
            toSnykCodeSettingsUrl("https://dev.app.eu.snyk.io"), equalTo("https://app.dev.eu.snyk.io/manage/snyk-code")
        )
        assertThat(
            toSnykCodeSettingsUrl("https://something.eu.snyk.io"), equalTo("https://app.snyk.io/manage/snyk-code")
        )
    }

    @Test
    fun `toSnykCodeSettingsUrl returns URL unedited for local engine`() {
        every { pluginSettings().localCodeEngineUrl } returns "http://foo.bar/api"
        every { pluginSettings().localCodeEngineEnabled } returns true

        assertEquals(toSnykCodeSettingsUrl("http://foo.bar/api"), "http://foo.bar/api")
    }

    @Test
    fun `toSnykCodeSettingsUrl returns correct settings url for Single Tenant deployments`() {
        val settingsUrl = toSnykCodeSettingsUrl("https://app.random-uuid.snyk.io/api")

        assertThat(settingsUrl, equalTo("https://app.random-uuid.snyk.io/manage/snyk-code"))
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
        every { pluginSettings().localCodeEngineUrl } returns "http://foo.bar"
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
    fun `isFedramp true for the right URI`() {
        val uri = URI("https://app.fedramp.snykgov.io")
        assertTrue(uri.isFedramp())
    }

    @Test
    fun `isFedramp false for the right URI`() {
        val uri = URI("https://app.fedddramp.snykagov.io")
        assertFalse(uri.isFedramp())
    }
}
