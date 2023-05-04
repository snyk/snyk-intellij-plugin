package snyk.common

import io.snyk.plugin.pluginSettings
import java.net.URI
import java.net.URISyntaxException

fun toSnykCodeApiUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)

    return when {
        uri.isSaaS() ->
            endpoint
                .replace("https://", "https://deeproxy.")
                .replace("api.", "")
                .removeSuffix("api")

        uri.isSnykTenant() ->
            endpoint
                .replace("api.", "")
                .replace("app", "deeproxy").removeSuffix("api")

        else ->
            "https://deeproxy.snyk.io/"
    }
}

fun toSnykCodeSettingsUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)

    val baseUrl = when {
        uri.isSaaS() -> endpoint.replace("https://", "https://app.").removeSuffix("api")
        uri.isSnykTenant() -> endpoint.removeSuffix("api")
        else -> "https://app.snyk.io/"
    }

    return "${baseUrl}manage/snyk-code"
}

fun needsSnykToken(endpoint: String): Boolean {
    val uri = URI(endpoint)
    return uri.isSnykApi() || uri.isSnykTenant()
}

fun getEndpointUrl(): String {
    val customEndpointUrl = resolveCustomEndpoint(pluginSettings().customEndpointUrl)
    return if (customEndpointUrl.endsWith('/')) customEndpointUrl else "$customEndpointUrl/"
}

fun isSnykCodeAvailable(endpointUrl: String?): Boolean {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)
    return uri.isSaaS() || uri.isSnykTenant()
}

/**
 * Resolves the custom endpoint.
 *
 * If the [endpointUrl] is null or empty, then [https://snyk.io/api](https://snyk.io/api) will be used.
 */
internal fun resolveCustomEndpoint(endpointUrl: String?): String {
    return if (endpointUrl.isNullOrEmpty()) {
        "https://snyk.io/api"
    } else {
        endpointUrl.removeTrailingSlashesIfPresent()
    }
}

/**
 * Checks if the deployment type is SaaS (production or development).
 */
fun URI.isSaaS() =
    this.host != null && !this.host.startsWith("app") && isSnykDomain()

/**
 * Checks if the deployment type is Single Tenant.
 */
fun URI.isSnykTenant() =
    this.host != null && this.host.startsWith("app") && isSnykDomain()

fun URI.isSnykApi() =
    this.host != null && (this.host.startsWith("api") || this.host.startsWith("deeproxy")) && (isSnykDomain()) ||
        this.host != null && isSnykDomain() && this.path.startsWith("/api")

fun URI.isSnykDomain() = this.host.endsWith("snyk.io") || this.host.endsWith("snykgov.io")

fun URI.isOauth() =
    this.host != null && this.host.endsWith("snykgov.io")

internal fun String.removeTrailingSlashesIfPresent(): String {
    val candidate = this.replace(Regex("/+$"), "")
    return try {
        URI(candidate)
        candidate
    } catch (e: URISyntaxException) {
        this
    }
}
