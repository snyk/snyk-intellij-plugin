package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.SnykCachedResults
import snyk.common.lsp.SnykScanParams

interface SnykScanListenerLS {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan LS", SnykScanListenerLS::class.java)
    }

    fun scanningStarted(snykScan: SnykScanParams) {}

    fun scanningSnykCodeFinished()

    fun scanningOssFinished()

    fun scanningError(snykScan: SnykScanParams)

    fun onPublishDiagnostics(product: String, snykResults: SnykCachedResults)
}
