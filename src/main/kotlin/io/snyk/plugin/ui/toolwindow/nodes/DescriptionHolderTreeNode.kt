package io.snyk.plugin.ui.toolwindow.nodes

import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel

interface DescriptionHolderTreeNode {
  fun getDescriptionPanel(): SuggestionDescriptionPanel
}
