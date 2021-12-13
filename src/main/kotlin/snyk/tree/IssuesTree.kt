package snyk.tree

import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import javax.swing.tree.DefaultMutableTreeNode

class IssuesTree(project: Project) : Tree() {
    private val root = DefaultMutableTreeNode("")
    private val ossIssuesNode = IssueTreeNode("Open Source Security", project)
    private val snykCodeSecurityIssuesNode = IssueTreeNode("Code Security", project)
    private val snykCodeQualityIssuesNode = IssueTreeNode("Code Quality", project)
    private val iacIssuesNode = IssueTreeNode("Configuration Issues", project)
    //private val containerIssuesNode = IssueTreeNode("Container", project)

    init {
        rootVisible = false

        root.add(ossIssuesNode)
        root.add(snykCodeSecurityIssuesNode)
        root.add(snykCodeQualityIssuesNode)
        root.add(iacIssuesNode)
    }
}

class IssueTreeNode(value: String, project: Project) : DefaultMutableTreeNode()
