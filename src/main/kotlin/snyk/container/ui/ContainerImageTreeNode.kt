package snyk.container.ui

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import snyk.container.ContainerIssuesForImage
import javax.swing.tree.DefaultMutableTreeNode

class ContainerImageTreeNode(
    private val issuesForImage: ContainerIssuesForImage,
    val project: Project,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(issuesForImage), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    override fun getDescriptionPanel(logEventNeeded: Boolean): IssueDescriptionPanelBase {
        // TODO: Add image click event logging ?
        return BaseImageRemediationDetailPanel(project, issuesForImage)
    }
}
