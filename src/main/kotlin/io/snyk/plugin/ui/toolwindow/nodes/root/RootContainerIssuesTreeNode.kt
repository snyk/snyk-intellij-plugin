package io.snyk.plugin.ui.toolwindow.nodes.root

import com.intellij.openapi.project.Project
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.SnykError

class RootContainerIssuesTreeNode(
    project: Project
) : RootTreeNodeBase(SnykToolWindowPanel.CONTAINER_ROOT_TEXT, project) {

    override fun getNoVulnerabilitiesMessage(): String {
        val nodeText = userObject as String
        return with(SnykToolWindowPanel) {
            when {
                nodeText.endsWith(NO_CONTAINER_IMAGES_FOUND) -> CONTAINER_NO_IMAGES_FOUND_TEXT
                nodeText.endsWith(NO_ISSUES_FOUND_TEXT) -> CONTAINER_NO_ISSUES_FOUND_TEXT
                else -> CONTAINER_SCAN_START_TEXT
            }
        }
    }

    override fun getScanningMessage(): String = SnykToolWindowPanel.CONTAINER_SCAN_RUNNING_TEXT

    override fun getSelectVulnerabilityMessage(): String {
        val nodeText = userObject as String
        return with(SnykToolWindowPanel) {
            when {
                nodeText.endsWith(NO_CONTAINER_IMAGES_FOUND) -> CONTAINER_NO_IMAGES_FOUND_TEXT
                nodeText.endsWith(NO_ISSUES_FOUND_TEXT) -> CONTAINER_NO_ISSUES_FOUND_TEXT
                else -> super.getSelectVulnerabilityMessage()
            }
        }
    }

    override fun getSnykError(): SnykError? = getSnykCachedResults(project)?.currentContainerError
}
