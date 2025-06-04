package snyk.common

import com.intellij.util.EnvironmentUtil
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings
import io.snyk.plugin.pluginSettings
import snyk.pluginInfo
import java.net.URLEncoder

object EnvironmentHelper {
    @Suppress("HttpUrlsUsage")
    fun updateEnvironment(
        environment: MutableMap<String, String>,
        apiToken: String,
    ) {
        // first of all, use IntelliJ environment tool, to spice up env
        environment.putAll(EnvironmentUtil.getEnvironmentMap())
        val endpoint = getEndpointUrl()

        val oauthEnabledEnvVar = "INTERNAL_SNYK_OAUTH_ENABLED"
        val oauthEnvVar = "INTERNAL_OAUTH_TOKEN_STORAGE"
        val snykTokenEnvVar = "SNYK_TOKEN"

        if (apiToken.isNotEmpty()) {
            environment.remove(snykTokenEnvVar)
            environment.remove(oauthEnvVar)
            environment.remove(oauthEnabledEnvVar)
            when (pluginSettings().useTokenAuthentication) {
                true -> {
                    environment[oauthEnabledEnvVar] = "0"
                    environment.remove(oauthEnvVar)
                    environment[snykTokenEnvVar] = apiToken
                }

                false -> {
                    environment[oauthEnabledEnvVar] = "1"
                    environment[oauthEnvVar] = apiToken
                    environment.remove(snykTokenEnvVar)
                }
            }
        }

        environment["SNYK_API"] = endpoint
        environment["SNYK_INTEGRATION_NAME"] = pluginInfo.integrationName
        environment["SNYK_INTEGRATION_VERSION"] = pluginInfo.integrationVersion
        environment["SNYK_INTEGRATION_ENVIRONMENT"] = pluginInfo.integrationEnvironment
        environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"] = pluginInfo.integrationEnvironmentVersion
        val proxyConfiguration = ProxySettings.getInstance().getProxyConfiguration()
        if (proxyConfiguration is ProxyConfiguration.StaticProxyConfiguration) {
            val proxyHost = proxyConfiguration.host
            val proxyPort = proxyConfiguration.port
            if (proxyConfiguration.protocol == ProxyConfiguration.ProxyProtocol.HTTP && proxyHost.isNotEmpty()) {
                val credentialStore = ProxyCredentialStore.getInstance()
                val credentials = credentialStore.getCredentials(proxyHost, proxyConfiguration.port)
                val userName = credentials?.userName
                val authentication =
                    if (credentials != null && userName != null && userName.isNotEmpty()) {
                        val password = credentials.password.toString()
                        userName.urlEncode() + ":" + password.urlEncode() + "@"
                    } else {
                        ""
                    }
                environment["http_proxy"] = "http://$authentication$proxyHost:$proxyPort"
                environment["https_proxy"] = "http://$authentication$proxyHost:$proxyPort"
                environment["no_proxy"] = proxyConfiguration.exceptions
            }
        }
    }

    fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
}
