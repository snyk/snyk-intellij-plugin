package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import io.snyk.plugin.snykcode.core.SnykCodeFile
import snyk.common.ProductType
import javax.swing.tree.DefaultMutableTreeNode

class SnykCodeFileTreeNode(
    file: SnykCodeFile,
    productType: ProductType
) : DefaultMutableTreeNode(Pair(file, productType))
