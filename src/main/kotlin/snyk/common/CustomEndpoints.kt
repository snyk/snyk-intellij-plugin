package snyk.common

import io.snyk.plugin.pluginSettings
import java.net.URI
import java.net.URISyntaxException

fun getEndpointUrl(): String {
  val endpointUrl =
    try {
      pluginSettings().customEndpointUrl
    } catch (_: RuntimeException) {
      // This is a workaround for the case when the plugin is not initialized yet.
      ""
    }
  val customEndpointUrl = resolveCustomEndpoint(endpointUrl)
  // we need to set v1 here, to make the sast-enabled calls work in LS
  return customEndpointUrl.removeSuffix()
}

/**
 * Resolves the custom endpoint.
 *
 * If the [endpointUrl] is null or empty, then [https://api.snyk.io](https://api.snyk.io) will be
 * used.
 */
internal fun resolveCustomEndpoint(endpointUrl: String?): String {
  return if (endpointUrl.isNullOrEmpty()) {
    val normalizedEndpointURL = "https://api.snyk.io"
    pluginSettings().customEndpointUrl = normalizedEndpointURL
    normalizedEndpointURL
  } else {
    val normalizedEndpointURL =
      endpointUrl
        .removeSuffix()
        .removeSuffix("/api")
        .replace("https://snyk.io", "https://api.snyk.io")
        .replace("https://app.", "https://api.")
    pluginSettings().customEndpointUrl = normalizedEndpointURL
    normalizedEndpointURL
  }
}

fun URI.isSnykTenant() =
  isSnykDomain() &&
    ((host.lowercase() == "snyk.io" && path.lowercase().endsWith("/api")) ||
      (host.lowercase().startsWith("api.") && !path.lowercase().endsWith("/api")) ||
      isDev())

fun URI.isSnykApi() =
  isSnykDomain() && (host.lowercase().startsWith("api.") || path.lowercase().endsWith("/api"))

fun URI.isSnykDomain() =
  host != null &&
    (host.lowercase().endsWith(".snyk.io") ||
      host.lowercase() == "snyk.io" ||
      host.lowercase().endsWith(".snykgov.io"))

fun URI.isDev() = isSnykDomain() && host.lowercase().startsWith("dev.")

fun URI.isAnalyticsPermitted() =
  host != null &&
    (host.lowercase() == "api.snyk.io" ||
      host.lowercase() == "api.us.snyk.io" ||
      host.lowercase() == "snyk.io")

internal fun String.removeSuffix(suffix: String = "/"): String {
  var candidate = this
  while (candidate.endsWith(suffix)) {
    candidate = candidate.substring(0, candidate.length - suffix.length)
  }
  try {
    URI(candidate)
    return candidate
  } catch (_: URISyntaxException) {
    return this
  }
}
