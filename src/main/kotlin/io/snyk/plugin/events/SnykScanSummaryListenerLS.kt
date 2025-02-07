package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.lsp.SnykScanSummaryParams

interface SnykScanSummaryListenerLS {
    companion object {
        val SNYK_SCAN_SUMMARY_TOPIC =
            Topic.create("Snyk scan summary LS", SnykScanSummaryListenerLS::class.java)
    }

    fun onSummaryReceived(summaryParams: SnykScanSummaryParams) {}
}
