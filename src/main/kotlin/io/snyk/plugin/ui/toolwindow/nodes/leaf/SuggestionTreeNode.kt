package io.snyk.plugin.ui.toolwindow.nodes.leaf

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import javax.swing.tree.DefaultMutableTreeNode
import snyk.common.lsp.ScanIssue

class SuggestionTreeNode(
  val project: Project,
  val issue: ScanIssue,
  override val navigateToSource: () -> Unit,
) : DefaultMutableTreeNode(issue), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

  override fun getDescriptionPanel(): SuggestionDescriptionPanel =
    SuggestionDescriptionPanel(project, issue)
}
