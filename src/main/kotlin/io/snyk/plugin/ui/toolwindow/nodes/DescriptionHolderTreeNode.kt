package io.snyk.plugin.ui.toolwindow.nodes

import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase

interface DescriptionHolderTreeNode {
    fun getDescriptionPanel(logEventNeeded : Boolean): IssueDescriptionPanelBase
}
