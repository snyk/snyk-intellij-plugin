package snyk.container.ui

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.NavigatableToSourceTreeNode
import snyk.container.ContainerIssuesForImage
import javax.swing.tree.DefaultMutableTreeNode

class ContainerImageTreeNode(
    issuesForImage: ContainerIssuesForImage,
    val project: Project,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(issuesForImage), NavigatableToSourceTreeNode
