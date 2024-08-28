package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import com.intellij.openapi.project.Project
import javax.swing.tree.DefaultMutableTreeNode

class FileTreeNode(
    ossVulnerabilitiesForFile: OssVulnerabilitiesForFile,
    val project: Project
) : DefaultMutableTreeNode(ossVulnerabilitiesForFile)
