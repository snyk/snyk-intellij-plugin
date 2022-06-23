package io.snyk.plugin.ui.toolwindow.nodes.root

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import io.snyk.plugin.ui.toolwindow.nodes.ErrorHolderTreeNode
import javax.swing.tree.DefaultMutableTreeNode

abstract class RootTreeNodeBase(
    userObject: Any,
    val project: Project
) : DefaultMutableTreeNode(userObject), ErrorHolderTreeNode {

    open fun getNoVulnerabilitiesMessage(): String = SnykToolWindowPanel.SCAN_PROJECT_TEXT

    open fun getScanningMessage(): String = SnykToolWindowPanel.SCANNING_TEXT

    open fun getSelectVulnerabilityMessage(): String = SnykToolWindowPanel.SELECT_ISSUE_TEXT
}
