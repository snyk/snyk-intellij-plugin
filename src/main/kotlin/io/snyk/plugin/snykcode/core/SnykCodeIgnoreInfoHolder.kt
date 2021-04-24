package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.DeepCodeIgnoreInfoHolderBase

class SnykCodeIgnoreInfoHolder private constructor() : DeepCodeIgnoreInfoHolderBase(
    HashContentUtils.instance,
    PDU.instance,
    SCLogger.instance
) {

    companion object{
        val instance = SnykCodeIgnoreInfoHolder()
    }
}
