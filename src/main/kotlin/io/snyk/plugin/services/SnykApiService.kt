package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.net.CliConfigSettings
import io.snyk.plugin.net.FalsePositivePayload
import io.snyk.plugin.net.RetrofitClientFactory
import io.snyk.plugin.net.SnykApiClient
import io.snyk.plugin.pluginSettings

@Service
class SnykApiService {
    fun getSastSettings(token: String? = pluginSettings().token): CliConfigSettings? {
        if (token == null) return null
        return getSnykApiClient(token)?.sastSettings(pluginSettings().organization)
    }

    val userId: String?
        get() = getSnykApiClient()?.getUserId()

    fun reportFalsePositive(payload: FalsePositivePayload): Boolean =
        getSnykApiClient()?.reportFalsePositive(payload) ?: false

    private fun getSnykApiClient(token: String? = pluginSettings().token): SnykApiClient? {
        if (token.isNullOrBlank()) {
            return null
        }
        val appSettings = pluginSettings()
        var endpoint = appSettings.customEndpointUrl
        if (endpoint.isNullOrEmpty()) endpoint = "https://snyk.io/api/"

        val baseUrl: String = if (endpoint.endsWith('/')) endpoint else "$endpoint/"

        log.debug("Creating new SnykApiClient")
        return try {
            val retrofit = RetrofitClientFactory.getInstance().createRetrofit(token, baseUrl)
            return SnykApiClient(retrofit)
        } catch (ignore: RuntimeException) {
            log.warn("Failed to create Retrofit client for endpoint: $endpoint", ignore)
            null
        }
    }

    companion object {
        private val log = logger<SnykApiService>()
    }
}
