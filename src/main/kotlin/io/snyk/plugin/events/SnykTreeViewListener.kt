package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.lsp.SnykTreeViewParams

interface SnykTreeViewListener {
  companion object {
    val SNYK_TREE_VIEW_TOPIC = Topic.create("Snyk tree view LS", SnykTreeViewListener::class.java)
  }

  fun onTreeViewReceived(params: SnykTreeViewParams) {}
}
