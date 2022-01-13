package snyk.container.ui

import com.intellij.openapi.project.Project
import snyk.container.ContainerIssuesForImage
import javax.swing.tree.DefaultMutableTreeNode

class ContainerImageTreeNode(
    issuesForImage: ContainerIssuesForImage,
    val project: Project
) : DefaultMutableTreeNode(issuesForImage)
