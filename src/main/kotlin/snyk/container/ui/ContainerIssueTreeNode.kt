package snyk.container.ui

import com.intellij.openapi.project.Project
import snyk.container.ContainerIssue
import javax.swing.tree.DefaultMutableTreeNode

class ContainerIssueTreeNode(
    issue: ContainerIssue,
    val project: Project
) : DefaultMutableTreeNode(issue)
