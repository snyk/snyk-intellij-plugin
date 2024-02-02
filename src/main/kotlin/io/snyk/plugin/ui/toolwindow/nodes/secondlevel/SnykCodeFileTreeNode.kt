package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import io.snyk.plugin.snykcode.core.SnykCodeFile
import snyk.common.ProductType
import snyk.common.lsp.ScanIssue
import javax.swing.tree.DefaultMutableTreeNode

class SnykCodeFileTreeNode(
    file: Map.Entry<SnykCodeFile, List<ScanIssue>>,
    productType: ProductType
) : DefaultMutableTreeNode(Pair(file, productType))
