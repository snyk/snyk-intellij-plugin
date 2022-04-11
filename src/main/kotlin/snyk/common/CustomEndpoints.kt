package snyk.common

import java.net.URI

fun toSnykCodeApiUrl(endpointUrl: String?): String {
    val endpoint = resolveCustomEndpoint(endpointUrl)
    val uri = URI(endpoint)

    return when {
        uri.isSaaS() -> endpoint.replace("https://", "https://deeproxy.").removeSuffix("api")
        uri.isSingleTenant() -> endpoint.replace("registry-web", "deeproxy").removeSuffix("api")
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
    this.host.endsWith("snyk.io")

/**
 * Checks if the deployment type is Single Tenant.
 */
internal fun URI.isSingleTenant() =
    this.host.contains("registry-web") && this.host.endsWith("snyk-internal.net")

private fun String.removeTrailingSlashesIfPresent(): String = this.replace(Regex("/+$"), "")
