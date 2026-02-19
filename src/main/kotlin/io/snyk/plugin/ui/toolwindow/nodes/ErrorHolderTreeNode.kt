package io.snyk.plugin.ui.toolwindow.nodes

import snyk.common.SnykError

interface ErrorHolderTreeNode {
  fun getSnykError(): SnykError?
}
