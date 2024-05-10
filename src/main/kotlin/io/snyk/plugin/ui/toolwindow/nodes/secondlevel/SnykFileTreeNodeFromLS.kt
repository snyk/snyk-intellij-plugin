package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import io.snyk.plugin.SnykFile
import snyk.common.ProductType
import snyk.common.lsp.ScanIssue
import javax.swing.tree.DefaultMutableTreeNode

class SnykFileTreeNodeFromLS(
    file: Map.Entry<SnykFile, List<ScanIssue>>,
    productType: ProductType
) : DefaultMutableTreeNode(Pair(file, productType))
