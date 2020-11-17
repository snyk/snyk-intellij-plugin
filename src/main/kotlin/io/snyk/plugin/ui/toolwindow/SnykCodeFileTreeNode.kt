package io.snyk.plugin.ui.toolwindow

import com.intellij.psi.PsiFile
import javax.swing.tree.DefaultMutableTreeNode

class SnykCodeFileTreeNode(file: PsiFile) : DefaultMutableTreeNode(file)
