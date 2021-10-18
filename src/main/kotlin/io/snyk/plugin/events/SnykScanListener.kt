package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.SnykError
import snyk.oss.OssResult
import io.snyk.plugin.snykcode.SnykCodeResults
import snyk.iac.IacResult

interface SnykScanListener {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan", SnykScanListener::class.java)
    }

    fun scanningStarted()

    fun scanningOssFinished(ossResult: OssResult)

    fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults?)

    fun scanningIacFinished(iacResult: IacResult)

    fun scanningOssError(snykError: SnykError)

    fun scanningIacError(snykError: SnykError)

    fun scanningSnykCodeError(snykError: SnykError)
}
