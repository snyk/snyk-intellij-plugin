package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeParamsBase
import io.snyk.plugin.getWaitForResultsTimeout
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.codeRestApi

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

    override fun consentGiven(project: Any): Boolean {
        return true
    }

    override fun setConsentGiven(project: Any) = Unit

    companion object {
        val instance = SnykCodeParams()
    }
}
