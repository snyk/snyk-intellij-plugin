package snyk.iac.ui.toolwindow

import com.intellij.openapi.project.Project
import snyk.iac.IacIssuesForFile
import javax.swing.tree.DefaultMutableTreeNode

class IacFileTreeNode(
    iacIssuesForFile: IacIssuesForFile,
    val project: Project
) : DefaultMutableTreeNode(iacIssuesForFile)
