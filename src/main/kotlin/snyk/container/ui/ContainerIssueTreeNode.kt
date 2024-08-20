package snyk.container.ui

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import snyk.container.ContainerIssue
import javax.swing.tree.DefaultMutableTreeNode

class ContainerIssueTreeNode(
    private val groupedVulns: List<ContainerIssue>,
    val project: Project,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(groupedVulns.first()), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    override fun getDescriptionPanel(): IssueDescriptionPanelBase {
        return ContainerIssueDetailPanel(groupedVulns)
    }
}
