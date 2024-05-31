package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import com.intellij.openapi.project.Project
import javax.swing.tree.DefaultMutableTreeNode

class InfoTreeNode(
    private val info: String,
    val project: Project,
) : DefaultMutableTreeNode(info)
