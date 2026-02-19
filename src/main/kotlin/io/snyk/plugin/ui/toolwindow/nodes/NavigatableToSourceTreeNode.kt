package io.snyk.plugin.ui.toolwindow.nodes

interface NavigatableToSourceTreeNode {
  val navigateToSource: () -> Unit
}
