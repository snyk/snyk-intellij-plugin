package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.project.Project
import snyk.oss.OssVulnerabilitiesForFile
import javax.swing.tree.DefaultMutableTreeNode

class FileTreeNode(
    ossVulnerabilitiesForFile: OssVulnerabilitiesForFile,
    val project: Project
) : DefaultMutableTreeNode(ossVulnerabilitiesForFile)
