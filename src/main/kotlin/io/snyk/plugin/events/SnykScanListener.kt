package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import io.snyk.plugin.SnykFile
import snyk.common.lsp.LsProduct
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams

interface SnykScanListener {
  companion object {
    val SNYK_SCAN_TOPIC = Topic.create("Snyk scan LS", SnykScanListener::class.java)
  }

  fun scanningStarted(snykScan: SnykScanParams) {}

  fun scanningSnykCodeFinished()

  fun scanningOssFinished()

  fun scanningIacFinished()

  fun scanningError(snykScan: SnykScanParams)

  fun onPublishDiagnostics(product: LsProduct, snykFile: SnykFile, issues: Set<ScanIssue>)
}
