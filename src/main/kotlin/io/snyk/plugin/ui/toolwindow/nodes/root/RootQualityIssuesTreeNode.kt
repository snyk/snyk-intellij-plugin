package io.snyk.plugin.ui.toolwindow.nodes.root

import com.intellij.openapi.project.Project
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.SnykError

class RootQualityIssuesTreeNode(
    project: Project
) : RootTreeNodeBase(SnykToolWindowPanel.CODE_QUALITY_ROOT_TEXT, project) {

    override fun getSnykError(): SnykError? = getSnykCachedResults(project)?.currentSnykCodeError
}
