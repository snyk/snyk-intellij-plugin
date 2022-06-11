package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.project.Project
import snyk.common.SnykError
import javax.swing.tree.DefaultMutableTreeNode

class ErrorTreeNode(
    snykError: SnykError,
    val project: Project,
    override val navigateToSource: () -> Unit
) : DefaultMutableTreeNode(snykError), NavigatableToSourceTreeNode
