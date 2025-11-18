package io.snyk.plugin.ui.toolwindow.nodes.root

import com.intellij.openapi.project.Project
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import io.snyk.plugin.ui.txtToHtml
import snyk.common.SnykError

class RootOssTreeNode(project: Project) : RootTreeNodeBase(SnykToolWindowPanel.OSS_ROOT_TEXT, project) {

    var originalCliErrorMessage: String? = null

    override fun getNoVulnerabilitiesMessage(): String =
        originalCliErrorMessage?.let { txtToHtml(it) } ?: super.getNoVulnerabilitiesMessage()

    override fun getSelectVulnerabilityMessage(): String =
        originalCliErrorMessage?.let { txtToHtml(it) } ?: super.getSelectVulnerabilityMessage()

    override fun getSnykError(): SnykError? = getSnykCachedResults(project)?.currentOssError?.toSnykError()
}
