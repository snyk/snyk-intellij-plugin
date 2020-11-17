package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.LoginUtilsBase

class LoginUtils private constructor() : LoginUtilsBase(
    PDU.instance,
    SnykCodeParams.instance,
    AnalysisData.instance,
    SCLogger.instance
) {

    override fun getUserAgent(): String = "SnykCode-Jetbrains"

    companion object {
        val instance = LoginUtils()
    }
}
