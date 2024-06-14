package snyk.common

import com.intellij.util.net.HttpConfigurable
import io.snyk.plugin.pluginSettings
import snyk.pluginInfo
import java.net.URI
import java.net.URLEncoder

object EnvironmentHelper {
    fun updateEnvironment(
        environment: MutableMap<String, String>,
        apiToken: String
    ) {
        val endpoint = getEndpointUrl()

        val oauthEnabledEnvVar = "INTERNAL_SNYK_OAUTH_ENABLED"
        val oauthEnvVar = "INTERNAL_OAUTH_TOKEN_STORAGE"
        val snykTokenEnvVar = "SNYK_TOKEN"

        val endpointURI = URI(endpoint)
        environment[oauthEnabledEnvVar] = "1"
        environment.remove(snykTokenEnvVar)

        if (apiToken.isNotEmpty()) {
            environment[oauthEnvVar] = apiToken
        }

        environment["SNYK_API"] = endpoint

        if (!pluginSettings().usageAnalyticsEnabled || !endpointURI.isAnalyticsPermitted()) {
            environment["SNYK_CFG_DISABLE_ANALYTICS"] = "1"
        }

        environment["SNYK_INTEGRATION_NAME"] = pluginInfo.integrationName
        environment["SNYK_INTEGRATION_VERSION"] = pluginInfo.integrationVersion
        environment["SNYK_INTEGRATION_ENVIRONMENT"] = pluginInfo.integrationEnvironment
        environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"] = pluginInfo.integrationEnvironmentVersion
        val proxySettings = HttpConfigurable.getInstance()
        val proxyHost = proxySettings.PROXY_HOST
        if (proxySettings != null && proxySettings.USE_HTTP_PROXY && proxyHost.isNotEmpty()) {
            val authentication = if (proxySettings.PROXY_AUTHENTICATION) {
                val auth = proxySettings.getPromptedAuthentication(proxyHost, "Snyk: Please enter your proxy password")
                if (auth == null) "" else auth.userName.urlEncode() + ":" + String(auth.password).urlEncode() + "@"
            } else ""
            environment["http_proxy"] = "http://$authentication$proxyHost:${proxySettings.PROXY_PORT}"
            environment["https_proxy"] = "http://$authentication$proxyHost:${proxySettings.PROXY_PORT}"
        }
    }

    private fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")
}
