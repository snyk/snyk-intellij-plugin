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
    fun getSastSettings(): CliConfigSettings? {
        return getSnykApiClient()?.sastSettings(pluginSettings().organization)
    }

    val userId: String?
        get() = getSnykApiClient()?.getUserId()

    fun reportFalsePositive(payload: FalsePositivePayload): Boolean =
        getSnykApiClient()?.reportFalsePositive(payload) ?: false

    private fun getSnykApiClient(): SnykApiClient? {
        if (pluginSettings().token.isNullOrBlank()) {
            return null
        }

        log.debug("Creating new SnykApiClient")
        return try {
            val retrofit = RetrofitClientFactory.getInstance().createRetrofit()
            return SnykApiClient(retrofit)
        } catch (ignore: RuntimeException) {
            log.warn("Failed to create Retrofit client", ignore)
            null
        }
    }

    companion object {
        private val log = logger<SnykApiService>()
    }
}
