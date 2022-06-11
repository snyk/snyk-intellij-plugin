package snyk.container.ui

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.NavigatableToSourceTreeNode
import snyk.container.ContainerIssue
import javax.swing.tree.DefaultMutableTreeNode

class ContainerIssueTreeNode(
    issue: ContainerIssue,
    val project: Project,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(issue), NavigatableToSourceTreeNode
