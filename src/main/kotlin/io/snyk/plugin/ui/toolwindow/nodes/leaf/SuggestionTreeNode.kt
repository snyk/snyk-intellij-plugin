package io.snyk.plugin.ui.toolwindow.nodes.leaf

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanelFromLS
import snyk.common.lsp.ScanIssue
import javax.swing.tree.DefaultMutableTreeNode

class SuggestionTreeNode(
    val project: Project,
    val issue: ScanIssue,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(issue), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    override fun getDescriptionPanel(): IssueDescriptionPanelBase {
        return SuggestionDescriptionPanelFromLS(project, issue)
    }
}
