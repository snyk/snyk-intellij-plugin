package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeParamsBase
import com.intellij.openapi.diagnostic.Logger
import io.snyk.plugin.getWaitForResultsTimeout
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.codeRestApi
import snyk.common.toSnykCodeApiUrl

class SnykCodeParams private constructor() : DeepCodeParamsBase(
    true,
    "ignored - will be set in init{}",
    false,
    false,
    1,
    pluginSettings().token,
    "",
    "${SCLogger.presentableName}-Jetbrains",
    pluginSettings().organization,
    { getWaitForResultsTimeout() },
    codeRestApi
) {
    fun requestLogging() = Logger.getInstance(SCLogger.presentableName + "RequestLogging").isDebugEnabled

    init {
        setApiUrl(
            toSnykCodeApiUrl(pluginSettings().customEndpointUrl),
            pluginSettings().ignoreUnknownCA,
            requestLogging()
        )
    }

    override fun setApiUrl(apiUrl: String) {
        setApiUrl(
            toSnykCodeApiUrl(apiUrl),
            pluginSettings().ignoreUnknownCA,
            requestLogging()
        )
    }

    override fun setApiUrl(apiUrl: String, disableSslVerification: Boolean) {
        setApiUrl(
            toSnykCodeApiUrl(apiUrl),
            disableSslVerification,
            requestLogging()
        )
    }

    override fun consentGiven(project: Any): Boolean {
        return true
    }

    override fun setConsentGiven(project: Any) = Unit

    companion object {
        val instance = SnykCodeParams()
    }
}
