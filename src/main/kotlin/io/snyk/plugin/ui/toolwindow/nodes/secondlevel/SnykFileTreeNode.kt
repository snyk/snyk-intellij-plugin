package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import io.snyk.plugin.SnykFile
import javax.swing.tree.DefaultMutableTreeNode
import snyk.common.ProductType
import snyk.common.lsp.ScanIssue

class SnykFileTreeNode(file: Map.Entry<SnykFile, List<ScanIssue>>, productType: ProductType) :
  DefaultMutableTreeNode(Pair(file, productType))
