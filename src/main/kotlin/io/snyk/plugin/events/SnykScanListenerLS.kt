package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import io.snyk.plugin.SnykFile
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams

interface SnykScanListenerLS {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan LS", SnykScanListenerLS::class.java)

        val PRODUCT_CODE = "code"
        val PRODUCT_OSS = "oss"
        val PRODUCT_IAC = "iac"
        val PRODUCT_CONTAINER = "container"
    }

    fun scanningStarted(snykScan: SnykScanParams) {}

    fun scanningSnykCodeFinished()

    fun scanningOssFinished()

    fun scanningIacFinished()

    fun scanningError(snykScan: SnykScanParams)

    fun onPublishDiagnostics(product: String, snykFile: SnykFile, issueList: List<ScanIssue>)
}
