package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.LoginUtilsBase

class LoginUtils private constructor() : LoginUtilsBase(
    PDU.instance,
    SnykCodeParams.instance,
    AnalysisData.instance,
    SCLogger.instance
) {

    override fun getUserAgent(): String = "${SCLogger.presentableName}-Jetbrains"

    companion object {
        val instance = LoginUtils()
    }
}
