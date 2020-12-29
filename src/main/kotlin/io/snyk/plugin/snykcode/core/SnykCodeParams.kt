package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeParamsBase

//TODO
class SnykCodeParams private constructor() : DeepCodeParamsBase(
    true,
    "https://www.deepcode.ai/",
    false,
    1,
    System.getenv("DEEPCODE_API_KEY")
        ?: throw IllegalArgumentException(
            "Environmental variable DEEPCODE_API_KEY with valid deepcode.ai token should be created!"
        ),
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
