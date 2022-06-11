package snyk.iac.ui.toolwindow

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.NavigatableToSourceTreeNode
import snyk.iac.IacIssue
import javax.swing.tree.DefaultMutableTreeNode

class IacIssueTreeNode(
    issue: IacIssue,
    val project: Project,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(issue), NavigatableToSourceTreeNode
