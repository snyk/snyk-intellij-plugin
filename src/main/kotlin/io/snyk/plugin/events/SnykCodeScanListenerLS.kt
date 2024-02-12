package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import io.snyk.plugin.snykcode.core.SnykCodeFile
import snyk.common.SnykError
import snyk.common.lsp.ScanIssue

interface SnykCodeScanListenerLS {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan LS", SnykCodeScanListenerLS::class.java)
    }

    fun scanningStarted()

    fun scanningSnykCodeFinished(snykCodeResults: Map<SnykCodeFile, List<ScanIssue>>)

    fun scanningSnykCodeError(snykError: SnykError)
}
