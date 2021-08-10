package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.project.Project
import snyk.iac.IacIssuesForFile
import javax.swing.tree.DefaultMutableTreeNode

class IacFileTreeNode(file: IacIssuesForFile, val project: Project) : DefaultMutableTreeNode(file)
