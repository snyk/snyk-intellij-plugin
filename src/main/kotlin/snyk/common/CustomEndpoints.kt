package snyk.common

import io.snyk.plugin.pluginSettings
import io.snyk.plugin.suffixIfNot
import java.net.URI
import java.net.URISyntaxException

fun toSnykCodeApiUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)

    val codeSubdomain = "deeproxy"
    val snykCodeApiUrl = when {

        uri.isDeeproxy() ->
            endpoint

        uri.isDev() ->
            endpoint
                .replace("api.", "")
                .replace("/v1", "")
                .replace("https://dev.", "https://$codeSubdomain.dev.")
                .suffixIfNot("/")

        uri.isSnykTenant() ->
            endpoint
                .replace("https://api.", "https://")
                .replace("/v1", "")
                .replace("https://", "https://$codeSubdomain.")
                .suffixIfNot("/")

        else -> "https://$codeSubdomain.snyk.io/"
    }
    return snykCodeApiUrl.removeSuffix("api").replace("app.", "")
}

fun toSnykCodeSettingsUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)
    val catchAllURL = "https://app.snyk.io"

    val baseUrl = when {

        uri.host == "snyk.io" ->
            "https://app.snyk.io/"

        uri.isDev() ->
            endpoint
                .replace("https://dev.api.", "https://app.dev.")
                .replace("https://dev.", "https://app.dev.")

        uri.isSnykTenant() ->
            endpoint.replace("https://api.", "https://app.")

        else -> catchAllURL
    }

    return baseUrl.removeSuffix("api").suffixIfNot("/") + "manage/snyk-code"
}

fun needsSnykToken(endpoint: String): Boolean {
    val uri = URI(endpoint)
    return uri.isSnykApi() || uri.isSnykTenant() || uri.isDeeproxy()
}

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

fun isSnykCodeAvailable(endpointUrl: String?): Boolean {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)
    return uri.isSnykTenant()
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
