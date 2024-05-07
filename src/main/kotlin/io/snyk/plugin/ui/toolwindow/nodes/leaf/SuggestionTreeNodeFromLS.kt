package io.snyk.plugin.ui.toolwindow.nodes.leaf

import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.SnykFile
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.FileTreeNodeFromLS
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanelFromLS
import snyk.analytics.IssueInTreeIsClicked.Ide
import snyk.analytics.IssueInTreeIsClicked.Severity
import snyk.analytics.IssueInTreeIsClicked.builder
import snyk.common.ProductType
import snyk.common.lsp.ScanIssue
import javax.swing.tree.DefaultMutableTreeNode

class SuggestionTreeNodeFromLS(
    private val issue: ScanIssue,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(issue), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    @Suppress("UNCHECKED_CAST")
    override fun getDescriptionPanel(logEventNeeded: Boolean): IssueDescriptionPanelBase {
        if (logEventNeeded) {
            getSnykAnalyticsService().logIssueInTreeIsClicked(
                builder()
                    .ide(Ide.JETBRAINS)
                    .issueType(issue.type())
                    .issueId(issue.id)
                    .severity(
                        when (issue.severity.lowercase()) {
                            "critical" -> Severity.CRITICAL
                            "high" -> Severity.HIGH
                            "medium" -> Severity.MEDIUM
                            "low" -> Severity.LOW
                            else -> Severity.LOW
                        }
                    )
                    .build()
            )
        }
        val snykFileTreeNode = this.parent as? FileTreeNodeFromLS
            ?: throw IllegalArgumentException(this.toString())

        @Suppress("UNCHECKED_CAST")
        val entry =
            (snykFileTreeNode.userObject as Pair<Map.Entry<SnykFile, List<ScanIssue>>, ProductType>).first
        val snykFile = entry.key
        return SuggestionDescriptionPanelFromLS(snykFile, issue)
    }
}
