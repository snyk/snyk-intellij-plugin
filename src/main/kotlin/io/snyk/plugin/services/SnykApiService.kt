package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.net.CliConfigSettings
import io.snyk.plugin.net.RetrofitClientFactory
import io.snyk.plugin.net.SnykApiClient
import io.snyk.plugin.pluginSettings
import snyk.common.getEndpointUrl
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.toSnykAPIv1
import java.net.URI

@Service
class SnykApiService {
    fun getSastSettings(): CliConfigSettings? = getSnykApiClient()?.sastSettings(pluginSettings().organization)

    val userId: String?
        get() {
            return LanguageServerWrapper.getInstance().getAuthenticatedUser()
        }

    private fun getSnykApiClient(): SnykApiClient? {
        if (pluginSettings().token.isNullOrBlank()) {
            return null
        }

        log.debug("Creating new SnykApiClient")
        return try {
            val apiUri = URI(getEndpointUrl()).toSnykAPIv1()
            val retrofit = RetrofitClientFactory.getInstance().createRetrofit(apiUri.toString())
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
