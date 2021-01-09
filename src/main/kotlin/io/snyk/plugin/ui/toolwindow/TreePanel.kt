package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout

class TreePanel(tree: Tree) : SimpleToolWindowPanel(false, true) {
    init {
        add(getMyToolbar().component, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(tree))
    }

    private fun getMyToolbar(): ActionToolbar {
        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction("io.snyk.plugin.TreeFilters") as ActionGroup
        return actionManager.createActionToolbar("Snyk Tree Toolbar", actionGroup, true)
    }
}
