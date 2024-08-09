package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import io.snyk.plugin.SnykFile
import snyk.common.SnykCachedResults
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams

interface SnykScanListenerLS {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan LS", SnykScanListenerLS::class.java)
    }

    fun scanningStarted(snykScan: SnykScanParams) {}

    fun scanningSnykCodeFinished(snykResults: Map<SnykFile, List<ScanIssue>>)

    fun scanningOssFinished(snykResults: Map<SnykFile, List<ScanIssue>>)

    fun scanningError(snykScan: SnykScanParams)

    fun onPublishDiagnostics(product: String, snykResults: SnykCachedResults)
}
