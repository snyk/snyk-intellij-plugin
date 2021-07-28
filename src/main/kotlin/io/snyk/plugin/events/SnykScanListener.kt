package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.SnykError
import snyk.oss.OssResult
import io.snyk.plugin.snykcode.SnykCodeResults

interface SnykScanListener {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan", SnykScanListener::class.java)
    }

    fun scanningStarted()

    fun scanningOssFinished(ossResult: OssResult)

    fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults)

    fun scanningOssError(snykError: SnykError)

    fun scanningSnykCodeError(snykError: SnykError)
}
