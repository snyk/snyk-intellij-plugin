package io.snyk.plugin.ui.toolwindow.nodes.secondlevel

import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.toolwindow.nodes.ErrorHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import snyk.common.SnykError
import javax.swing.tree.DefaultMutableTreeNode

class ErrorTreeNode(
    private val snykError: SnykError,
    val project: Project,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(snykError), NavigatableToSourceTreeNode, ErrorHolderTreeNode {

    override fun getSnykError(): SnykError = snykError
}
