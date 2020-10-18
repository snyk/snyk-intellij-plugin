package io.snyk.plugin.ui.toolwindow

import javax.swing.tree.DefaultMutableTreeNode

class FileTreeNode(displayTargetFile: String, packageManager: String)
    : DefaultMutableTreeNode(displayTargetFile to packageManager)
