package io.snyk.plugin.ui.toolwindow

import snyk.iac.IacIssue
import javax.swing.tree.DefaultMutableTreeNode

class IacIssueTreeNode(
    issue: IacIssue
) : DefaultMutableTreeNode(issue)
