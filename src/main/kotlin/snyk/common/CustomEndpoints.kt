package snyk.common

import io.snyk.plugin.pluginSettings
import io.snyk.plugin.prefixIfNot
import io.snyk.plugin.suffixIfNot
import java.net.URI
import java.net.URISyntaxException

fun toSnykCodeApiUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)

    val codeSubdomain = "deeproxy"
    val snykCodeApiUrl = when {
        isLocalCodeEngine() ->
            return pluginSettings().localCodeEngineUrl!!

        uri.isDeeproxy() ->
            endpoint

        uri.isDev() ->
            endpoint
                .replace("api.", "")
                .replace("https://dev.", "https://$codeSubdomain.dev.")
                .suffixIfNot("/")

        uri.isSnykTenant() ->
            endpoint
                .replace("https://api.", "https://")
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
        isLocalCodeEngine() -> return endpointUrl?.replace("https://api.", "https://app.") ?: catchAllURL

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
    return uri.isSnykApi() || uri.isSnykTenant() || uri.isDeeproxy() || isLocalCodeEngine()
}

fun getEndpointUrl(): String {
    val endpointUrl = try {
        pluginSettings().customEndpointUrl
    } catch (e: RuntimeException) {
        // This is a workaround for the case when the plugin is not initialized yet.
        ""
    }
    val customEndpointUrl = resolveCustomEndpoint(endpointUrl)
    return customEndpointUrl.removeTrailingSlashesIfPresent()
}

fun isSnykCodeAvailable(endpointUrl: String?): Boolean {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)
    return uri.isSnykTenant() || isLocalCodeEngine()
}

/**
 * Resolves the custom endpoint.
 *
 * If the [endpointUrl] is null or empty, then [https://api.snyk.io](https://api.snyk.io) will be used.
 */
internal fun resolveCustomEndpoint(endpointUrl: String?): String {
    return if (endpointUrl.isNullOrEmpty()) {
        "https://api.snyk.io"
    } else {
        endpointUrl
            .removeTrailingSlashesIfPresent()
            .removeSuffix("/api")
            .replace("https://app.", "https://api.")
    }
}

fun URI.isSnykTenant() =
    isSnykDomain()
        && ((host.lowercase().startsWith("app.") && path.lowercase().endsWith("/api"))
        || (host.lowercase() == "snyk.io" && path.lowercase().endsWith("/api"))
        || (host.lowercase().startsWith("api.") && !path.lowercase().endsWith("/api"))
        || isDev())

fun URI.isSnykApi() = isSnykDomain() && (host.lowercase().startsWith("api.") || path.lowercase().endsWith("/api"))

fun URI.toSnykAPIv1(): URI {
    val host = host.lowercase()
        .replaceFirst("app.", "api.")
        .replaceFirst("deeproxy.", "api.")
        .prefixIfNot(
            "api."
        )

    return URI(scheme, host, "/v1/", null)
}

fun URI.isSnykDomain() = host != null &&
    (
        host.lowercase().endsWith(".snyk.io") ||
            host.lowercase() == "snyk.io" ||
            host.lowercase().endsWith(".snykgov.io")
        )

fun URI.isDeeproxy() = isSnykDomain() && host.lowercase().startsWith("deeproxy.")

fun URI.isSnykGov() = host != null && host.lowercase().endsWith(".snykgov.io")

fun URI.isOauth() = isSnykGov()

fun URI.isDev() = isSnykDomain() && host.lowercase().startsWith("dev.")

fun URI.isAnalyticsPermitted() = host != null &&
    (host.lowercase() == "app.snyk.io" || host.lowercase() == "app.us.snyk.io" || host.lowercase() == "snyk.io")

fun isAnalyticsPermitted(): Boolean {
    val settings = pluginSettings()
    return settings.customEndpointUrl
        ?.let { URI(it) }
        ?.isAnalyticsPermitted() ?: true
}

fun isLocalCodeEngine() = pluginSettings().localCodeEngineEnabled == true

internal fun String.removeTrailingSlashesIfPresent(): String {
    val candidate = this.replace(Regex("/+$"), "")
    return try {
        URI(candidate)
        candidate
    } catch (ignored: URISyntaxException) {
        this
    }
}
