package snyk.common

import io.snyk.plugin.pluginSettings
import org.jetbrains.kotlin.util.prefixIfNot
import org.jetbrains.kotlin.util.suffixIfNot
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
            endpoint.replace("https://dev.", "https://$codeSubdomain.dev.")

        uri.isSnykTenant() ->
            endpoint.replace("https://", "https://$codeSubdomain.")

        else -> "https://$codeSubdomain.snyk.io/"
    }
    return snykCodeApiUrl.removeSuffix("api").replace("app.", "")
}

fun toSnykCodeSettingsUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)
    val baseUrl = when {
        uri.host == "snyk.io" ->
            "https://app.snyk.io/"

        uri.isDev() ->
            endpoint
                .replace("https://dev.app.", "https://app.dev.")
                .replace("https://dev.", "https://app.dev.")

        uri.isSnykTenant() ->
            endpoint

        else ->
            "https://app.snyk.io/"
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
    } catch (e: RuntimeException) {
        // This is a workaround for the case when the plugin is not initialized yet.
        ""
    }
    val customEndpointUrl = resolveCustomEndpoint(endpointUrl)
    return if (customEndpointUrl.endsWith('/')) customEndpointUrl else "$customEndpointUrl/"
}

fun isSnykCodeAvailable(endpointUrl: String?): Boolean {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)
    return uri.isSnykTenant()
}

/**
 * Resolves the custom endpoint.
 *
 * If the [endpointUrl] is null or empty, then [https://app.snyk.io/api](https://appsnyk.io/api) will be used.
 */
internal fun resolveCustomEndpoint(endpointUrl: String?): String {
    return if (endpointUrl.isNullOrEmpty()) {
        "https://app.snyk.io/api"
    } else {
        endpointUrl.removeTrailingSlashesIfPresent()
    }
}

fun URI.isSnykTenant() =
    isSnykDomain() && (host.startsWith("app.") || host == "snyk.io" || isDev()) && path.endsWith("/api")

fun URI.isSnykApi() = isSnykDomain() && (host.startsWith("api.") || path.endsWith("/api"))

fun URI.toSnykAPIv1(): URI {
    val host = host.replaceFirst("app.", "api.").replaceFirst("deeproxy.", "api.").prefixIfNot("api.")

    return URI(scheme, host, "/v1/", null)
}

fun URI.isSnykDomain() = host != null && (host.endsWith("snyk.io") || host.endsWith("snykgov.io"))

fun URI.isDeeproxy() = isSnykDomain() && host.startsWith("deeproxy.")

fun URI.isOauth() = host != null && host.endsWith(".snykgov.io")

fun URI.isDev() = isSnykDomain() && host.startsWith("dev.")

internal fun String.removeTrailingSlashesIfPresent(): String {
    val candidate = this.replace(Regex("/+$"), "")
    return try {
        URI(candidate)
        candidate
    } catch (ignored: URISyntaxException) {
        this
    }
}
