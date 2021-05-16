package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeParamsBase
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.toSnykCodeApiUrl

class SnykCodeParams private constructor() : DeepCodeParamsBase(
    true,
    "ignored - will be set in init{}",
    false,
    false,
    1,
    getApplicationSettingsStateService().token,
    "",
    "${SCLogger.presentableName}-Jetbrains"
) {

    init {
        setApiUrl(toSnykCodeApiUrl(getApplicationSettingsStateService().customEndpointUrl), getApplicationSettingsStateService().ignoreUnknownCA)
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
