package snyk.common

import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import java.net.URI
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class CustomEndpointsTest {
  lateinit var settings: SnykApplicationSettingsStateService

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
    val endpointWithMultipleTrailingSlashes =
      resolveCustomEndpoint("https://app.on-prem.internal/api///")

    assertEquals("https://api.on-prem.internal", endpointWithSingleTrailingSlash)
    assertEquals("https://api.on-prem.internal", endpointWithMultipleTrailingSlashes)
  }

  @Test
  fun `removeTrailingSlashesIfPresent do not return malformed(syntactically incorrect) url`() {
    val endpointWithNoHost = "http:/".removeSuffix()

    assertEquals("http:/", endpointWithNoHost)
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
  fun `isAnalyticsPermitted false for URIs not allowed`() {
    val uris =
      listOf("https://api.fedramp.snykgov.io", "https://api.eu.snyk.io", "https://api.au.snyk.io")
    uris.forEach { uri -> assertFalse(URI(uri).isAnalyticsPermitted()) }
  }

  @Test
  fun `isAnalyticsPermitted true for the right URIs`() {
    val uris =
      listOf(
        "https://snyk.io/api",
        "https://api.snyk.io",
        "https://api.us.snyk.io",
        "https://api.snyk.io",
        "https://api.snyk.io/v1",
      )

    uris.forEach { uri -> assertTrue(URI(uri).isAnalyticsPermitted()) }
  }
}
