package snyk.tree

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project

class BaseIssueNode(project: Project, value: String) : AbstractTreeNode<String>(project, value) {
    override fun update(presentation: PresentationData) {
        TODO("will be added with tree view PR")
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        TODO("will be added with tree view PR")
    }
}
