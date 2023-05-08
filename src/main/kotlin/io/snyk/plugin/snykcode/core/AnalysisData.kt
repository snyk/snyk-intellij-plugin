package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.AnalysisDataBase
import io.snyk.plugin.snykcode.newCodeRestApi

class AnalysisData private constructor() : AnalysisDataBase(
    PDU.instance,
    HashContentUtils.instance,
    SnykCodeParams.instance,
    SCLogger.instance,
    newCodeRestApi()
) {
    override fun updateUIonFilesRemovalFromCache(files: MutableCollection<Any>) {
        //probably not needed yet
    }

    companion object {
        val instance = AnalysisData()
    }
}
