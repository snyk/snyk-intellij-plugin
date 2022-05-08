package snyk.common

import java.net.URI
import java.net.URISyntaxException

fun toSnykCodeApiUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)

    return when {
        uri.isSaaS() -> endpoint.replace("https://", "https://deeproxy.").removeSuffix("api")
        uri.isSingleTenant() -> endpoint.replace("app", "deeproxy").removeSuffix("api")
        else -> "https://deeproxy.snyk.io/"
    }
}

fun toSnykCodeSettingsUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)

    val baseUrl = when {
        uri.isSaaS() -> endpoint.replace("https://", "https://app.").removeSuffix("api")
        uri.isSingleTenant() -> endpoint.removeSuffix("api")
        else -> "https://app.snyk.io/"
    }

    return "${baseUrl}manage/snyk-code"
}

fun isSnykCodeAvailable(endpointUrl: String?): Boolean {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)
    return uri.isSaaS() || uri.isSingleTenant()
}

/**
 * Resolves the custom endpoint.
 *
 * If the [endpointUrl] is null or empty, then [https://snyk.io/api](https://snyk.io/api) will be used.
 */
internal fun resolveCustomEndpoint(endpointUrl: String?): String {
    return if (endpointUrl == null || endpointUrl.isEmpty()) {
        "https://snyk.io/api"
    } else {
        endpointUrl.removeTrailingSlashesIfPresent()
    }
}

/**
 * Checks if the deployment type is SaaS (production or development).
 */
internal fun URI.isSaaS() =
    this.host != null && !this.host.startsWith("app") && this.host.endsWith("snyk.io")

/**
 * Checks if the deployment type is Single Tenant.
 */
internal fun URI.isSingleTenant() =
    this.host != null && this.host.startsWith("app") && this.host.endsWith("snyk.io")

internal fun String.removeTrailingSlashesIfPresent(): String {
    val candidate = this.replace(Regex("/+$"), "")
    return try {
        URI(candidate)
        candidate
    } catch (e: URISyntaxException) {
        this
    }
}
