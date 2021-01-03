package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeParamsBase
import io.snyk.plugin.getApplicationSettingsStateService

//TODO
class SnykCodeParams private constructor() : DeepCodeParamsBase(
    true,
    "https://www.deepcode.ai/",
    false,
    1,
    if (getApplicationSettingsStateService().deepcodeToken.isNotEmpty()) {
        getApplicationSettingsStateService().deepcodeToken
    } else {
        System.getenv("DEEPCODE_API_KEY") ?: "" // for CI tests only
    },
    "",
    "${SCLogger.presentableName}-Jetbrains"
) {

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
