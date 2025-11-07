package snyk.common

import io.snyk.plugin.pluginSettings
import java.net.URI
import java.net.URISyntaxException

fun getEndpointUrl(): String {
    val endpointUrl = try {
        pluginSettings().customEndpointUrl
    } catch (_: RuntimeException) {
        // This is a workaround for the case when the plugin is not initialized yet.
        ""
    }
    val customEndpointUrl = resolveCustomEndpoint(endpointUrl)
    // we need to set v1 here, to make the sast-enabled calls work in LS
    return customEndpointUrl.removeTrailingSlashesIfPresent()
}

/**
 * Resolves the custom endpoint.
 *
 * If the [endpointUrl] is null or empty, then [https://api.snyk.io](https://api.snyk.io) will be used.
 */
internal fun resolveCustomEndpoint(endpointUrl: String?): String {
    return if (endpointUrl.isNullOrEmpty()) {
        val normalizedEndpointURL = "https://api.snyk.io"
        pluginSettings().customEndpointUrl = normalizedEndpointURL
        normalizedEndpointURL
    } else {
        val normalizedEndpointURL = endpointUrl
            .removeTrailingSlashesIfPresent()
            .removeSuffix("/api")
            .replace("https://snyk.io", "https://api.snyk.io")
            .replace("https://app.", "https://api.")
        pluginSettings().customEndpointUrl = normalizedEndpointURL
        normalizedEndpointURL
    }
}

fun URI.isSnykTenant() =
    isSnykDomain()
        && ((host.lowercase() == "snyk.io" && path.lowercase().endsWith("/api"))
        || (host.lowercase().startsWith("api.") && !path.lowercase().endsWith("/api"))
        || isDev())

fun URI.isSnykApi() = isSnykDomain() && (host.lowercase().startsWith("api.") || path.lowercase().endsWith("/api"))

fun URI.isSnykDomain() = host != null &&
    (
        host.lowercase().endsWith(".snyk.io") ||
            host.lowercase() == "snyk.io" ||
            host.lowercase().endsWith(".snykgov.io")
        )

fun URI.isDeeproxy() = isSnykDomain() && host.lowercase().startsWith("deeproxy.")

fun URI.isDev() = isSnykDomain() && host.lowercase().startsWith("dev.")

fun URI.isAnalyticsPermitted() = host != null &&
    (host.lowercase() == "api.snyk.io" || host.lowercase() == "api.us.snyk.io" || host.lowercase() == "snyk.io")

internal fun String.removeTrailingSlashesIfPresent(): String {
    val candidate = this.replace(Regex("/+$"), "")
    return try {
        URI(candidate)
        candidate
    } catch (_: URISyntaxException) {
        this
    }
}
