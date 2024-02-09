package io.snyk.plugin.ui.toolwindow.nodes.leaf

import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykCodeFileTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import snyk.analytics.IssueInTreeIsClicked.Ide
import snyk.analytics.IssueInTreeIsClicked.IssueType.CODE_QUALITY_ISSUE
import snyk.analytics.IssueInTreeIsClicked.IssueType.CODE_SECURITY_VULNERABILITY
import snyk.analytics.IssueInTreeIsClicked.Severity
import snyk.analytics.IssueInTreeIsClicked.builder
import snyk.common.ProductType
import snyk.common.lsp.ScanIssue
import javax.swing.tree.DefaultMutableTreeNode

class SuggestionTreeNode(
    private val issue: ScanIssue,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(issue), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    @Suppress("UNCHECKED_CAST")
    override fun getDescriptionPanel(logEventNeeded: Boolean): IssueDescriptionPanelBase {
        val issueType = if (issue.additionalData.isSecurityType) {
            CODE_SECURITY_VULNERABILITY
        } else {
            CODE_QUALITY_ISSUE
        }

        if (logEventNeeded) {
            getSnykAnalyticsService().logIssueInTreeIsClicked(
                builder()
                    .ide(Ide.JETBRAINS)
                    .issueType(issueType)
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
        val snykCodeFileTreeNode = this.parent as? SnykCodeFileTreeNode
            ?: throw IllegalArgumentException(this.toString())

        @Suppress("UNCHECKED_CAST")
        val entry =
            (snykCodeFileTreeNode.userObject as Pair<Map.Entry<SnykCodeFile, List<ScanIssue>>, ProductType>).first
        val snykCodeFile = entry.key
        return SuggestionDescriptionPanel(snykCodeFile, issue)
    }
}
