package io.snyk.plugin.ui.toolwindow

import io.snyk.plugin.cli.CliVulnerabilitiesForFile
import javax.swing.tree.DefaultMutableTreeNode

class FileTreeNode(cliVulnerabilitiesForFile: CliVulnerabilitiesForFile)
    : DefaultMutableTreeNode(cliVulnerabilitiesForFile)
