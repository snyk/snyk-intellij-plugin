package snyk.container.ui

import com.intellij.openapi.project.Project
import io.snyk.plugin.analytics.getIssueSeverityOrNull
import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import snyk.analytics.IssueInTreeIsClicked
import snyk.container.ContainerIssue
import javax.swing.tree.DefaultMutableTreeNode

class ContainerIssueTreeNode(
    private val groupedVulns: List<ContainerIssue>,
    val project: Project,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(groupedVulns.first()), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    override fun getDescriptionPanel(logEventNeeded: Boolean): IssueDescriptionPanelBase {
        if (logEventNeeded) getSnykAnalyticsService().logIssueInTreeIsClicked(
            IssueInTreeIsClicked.builder()
                .ide(IssueInTreeIsClicked.Ide.JETBRAINS)
                .issueType(IssueInTreeIsClicked.IssueType.CONTAINER_VULNERABILITY)
                .issueId(groupedVulns.first().id)
                .severity(groupedVulns.first().getIssueSeverityOrNull())
                .build()
        )
        return ContainerIssueDetailPanel(groupedVulns)
    }
}
