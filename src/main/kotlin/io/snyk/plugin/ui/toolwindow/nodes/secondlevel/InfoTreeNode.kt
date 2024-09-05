package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.tree.DefaultMutableTreeNode

open class InfoTreeNode(
    open val info: String,
    open val project: Project,
) : DefaultMutableTreeNode(info)

class ChooseBranchNode(override val info: String = "Choose base branch", override val project: Project) : InfoTreeNode(info, project) {
    val icon = AllIcons.Vcs.BranchNode
}
