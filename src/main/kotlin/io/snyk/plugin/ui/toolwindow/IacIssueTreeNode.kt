package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.project.Project
import snyk.iac.IacIssue
import javax.swing.tree.DefaultMutableTreeNode

class IacIssueTreeNode(
    issue: IacIssue,
    val project: Project
) : DefaultMutableTreeNode(issue)
