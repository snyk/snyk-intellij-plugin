package io.snyk.plugin.ui.toolwindow.nodes.leaf

import ai.deepcode.javaclient.core.SuggestionForFile
import io.snyk.plugin.analytics.getIssueSeverityOrNull
import io.snyk.plugin.analytics.getIssueType
import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykCodeFileTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import snyk.analytics.IssueInTreeIsClicked
import javax.swing.tree.DefaultMutableTreeNode

class SuggestionTreeNode(
    private val suggestion: SuggestionForFile,
    private val rangeIndex: Int,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(Pair(suggestion, rangeIndex)), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    override fun getDescriptionPanel(logEventNeeded: Boolean): IssueDescriptionPanelBase {
        if (logEventNeeded) getSnykAnalyticsService().logIssueInTreeIsClicked(
            IssueInTreeIsClicked.builder()
                .ide(IssueInTreeIsClicked.Ide.JETBRAINS)
                .issueType(suggestion.getIssueType())
                .issueId(suggestion.id)
                .severity(suggestion.getIssueSeverityOrNull())
                .build()
        )
        val snykCodeFile = (this.parent as? SnykCodeFileTreeNode)?.userObject as? SnykCodeFile
            ?: throw IllegalArgumentException(this.toString())
        return SuggestionDescriptionPanel(snykCodeFile, suggestion, rangeIndex)
    }
}
