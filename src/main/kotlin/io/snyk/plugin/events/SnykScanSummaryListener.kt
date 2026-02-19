package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.lsp.SnykScanSummaryParams

interface SnykScanSummaryListener {
  companion object {
    val SNYK_SCAN_SUMMARY_TOPIC =
      Topic.create("Snyk scan summary LS", SnykScanSummaryListener::class.java)
  }

  fun onSummaryReceived(summaryParams: SnykScanSummaryParams) {}
}
