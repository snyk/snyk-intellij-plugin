package io.snyk.plugin.snykcode.core

import ai.deepcode.javaclient.core.AnalysisDataBase

class AnalysisData private constructor() : AnalysisDataBase(
    PDU.instance,
    HashContentUtils.instance,
    SnykCodeParams.instance,
    SCLogger.instance
) {
    override fun updateUIonFilesRemovalFromCache(files: MutableCollection<Any>) {
        //probably not needed yet
    }

    companion object {
        val instance = AnalysisData()
    }
}
