package io.snyk.plugin.ui.toolwindow.nodes.leaf

import io.snyk.plugin.SnykFile
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykFileTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanelFromLS
import snyk.common.ProductType
import snyk.common.lsp.ScanIssue
import javax.swing.tree.DefaultMutableTreeNode

class SuggestionTreeNode(
    private val issue: ScanIssue,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(issue), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    @Suppress("UNCHECKED_CAST")
    override fun getDescriptionPanel(): IssueDescriptionPanelBase {
        val snykFileTreeNode = this.parent as? SnykFileTreeNode
            ?: throw IllegalArgumentException(this.toString())

        @Suppress("UNCHECKED_CAST")
        val entry =
            (snykFileTreeNode.userObject as Pair<Map.Entry<SnykFile, List<ScanIssue>>, ProductType>).first
        val snykFile = entry.key
        return SuggestionDescriptionPanelFromLS(snykFile, issue)
    }
}
