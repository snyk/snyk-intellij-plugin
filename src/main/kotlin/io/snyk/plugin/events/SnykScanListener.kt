package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import io.snyk.plugin.snykcode.core.SnykCodeFile
import snyk.common.SnykError
import snyk.common.lsp.ScanIssue
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult

interface SnykScanListener {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan", SnykScanListener::class.java)
    }

    fun scanningStarted()

    fun scanningOssFinished(ossResult: OssResult)

    fun scanningSnykCodeFinished(snykCodeResults: Map<SnykCodeFile, List<ScanIssue>>)

    fun scanningIacFinished(iacResult: IacResult)

    fun scanningOssError(snykError: SnykError)

    fun scanningIacError(snykError: SnykError)

    fun scanningSnykCodeError(snykError: SnykError)

    fun scanningContainerFinished(containerResult: ContainerResult)

    fun scanningContainerError(snykError: SnykError)
}
