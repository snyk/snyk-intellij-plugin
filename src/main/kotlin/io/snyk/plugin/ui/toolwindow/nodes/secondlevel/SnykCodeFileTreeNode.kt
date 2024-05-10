package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import io.snyk.plugin.SnykFile
import snyk.common.ProductType
import javax.swing.tree.DefaultMutableTreeNode

class SnykCodeFileTreeNode(
    file: SnykFile,
    productType: ProductType
) : DefaultMutableTreeNode(Pair(file, productType))
