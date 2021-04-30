package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliVulnerabilitiesForFile
import javax.swing.tree.DefaultMutableTreeNode

class FileTreeNode(
    cliVulnerabilitiesForFile: CliVulnerabilitiesForFile,
    val project: Project
) : DefaultMutableTreeNode(cliVulnerabilitiesForFile)
