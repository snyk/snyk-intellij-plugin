package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.AnalysisDataBase
import io.snyk.plugin.snykcode.codeRestApi

class AnalysisData private constructor() : AnalysisDataBase(
    PDU.instance,
    HashContentUtils.instance,
    SnykCodeParams.instance,
    SCLogger.instance,
    codeRestApi
) {
    override fun updateUIonFilesRemovalFromCache(files: MutableCollection<Any>) {
        //probably not needed yet
    }

    companion object {
        val instance = AnalysisData()
    }
}
