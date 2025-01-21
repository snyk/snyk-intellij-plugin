package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

class TreePanel(tree: Tree) : SimpleToolWindowPanel(true, true) {
    private val actionManager = ActionManager.getInstance()
    private val scanSummaryPanel = SimpleToolWindowPanel(true, true).apply { name = "summaryPanel" }

    init {
        name = "treePanel"
        val severityToolbarPanel = JPanel(BorderLayout())
        severityToolbarPanel.add(JLabel("  Severity: "), BorderLayout.WEST)
        val severityToolbar = getSeverityToolbar()
        severityToolbar.targetComponent = this
        severityToolbarPanel.add(severityToolbar.component, BorderLayout.CENTER)

        val toolBarPanel = JPanel(BorderLayout())
        toolBarPanel.add(severityToolbarPanel, BorderLayout.CENTER)

        add(toolBarPanel, BorderLayout.NORTH)
        toolbar = toolBarPanel

        val treePanel = SimpleToolWindowPanel(true, false)
        treePanel.add(ScrollPaneFactory.createScrollPane(tree))
        treePanel.border = IdeBorderFactory.createBorder(SideBorder.TOP)
        add(treePanel)
    }

    private fun getSeverityToolbar(): ActionToolbar {
        val actionGroup = actionManager.getAction("io.snyk.plugin.TreeFilters.Severity") as ActionGroup
        return actionManager.createActionToolbar("Snyk Tree Toolbar", actionGroup, true)
    }
}
