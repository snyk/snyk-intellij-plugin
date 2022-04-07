package io.snyk.plugin.ui.toolwindow

import io.snyk.plugin.snykcode.core.SnykCodeFile
import javax.swing.tree.DefaultMutableTreeNode

class SnykCodeFileTreeNode(file: SnykCodeFile) : DefaultMutableTreeNode(file)
