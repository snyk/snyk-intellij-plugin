package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeParamsBase
import io.snyk.plugin.getWaitForResultsTimeout
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.toSnykCodeApiUrl

class SnykCodeParams private constructor() : DeepCodeParamsBase(
    true,
    "ignored - will be set in init{}",
    false,
    false,
    1,
    pluginSettings().token,
    "",
    "${SCLogger.presentableName}-Jetbrains",
    { getWaitForResultsTimeout() }
) {

    init {
        setApiUrl(toSnykCodeApiUrl(pluginSettings().customEndpointUrl), pluginSettings().ignoreUnknownCA)
    }

    override fun consentGiven(project: Any): Boolean {
        //TODO
        return true
    }

    override fun setConsentGiven(project: Any) {
        //TODO
    }

    companion object {
        val instance = SnykCodeParams()
    }
}
