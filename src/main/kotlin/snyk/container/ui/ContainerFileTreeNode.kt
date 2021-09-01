package snyk.container.ui

import com.intellij.openapi.project.Project
import snyk.container.ContainerIssuesForFile
import javax.swing.tree.DefaultMutableTreeNode

class ContainerFileTreeNode(
    containerIssuesForFile: ContainerIssuesForFile,
    val project: Project
) : DefaultMutableTreeNode(containerIssuesForFile)
