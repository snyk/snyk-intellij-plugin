package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.treeStructure.Tree
import io.snyk.plugin.events.SnykFolderConfigListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

class TreePanel(tree: Tree, project: Project, parentDisposable: Disposable) :
  SimpleToolWindowPanel(true, true) {
  private val actionManager = ActionManager.getInstance()
  private val severityToolbar: ActionToolbar

  init {
    name = "treePanel"
    val severityToolbarPanel = JPanel(BorderLayout())
    severityToolbarPanel.add(JLabel("  Severity: "), BorderLayout.WEST)
    severityToolbar = getSeverityToolbar()
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

    val connection = project.messageBus.connect(parentDisposable)

    connection.subscribe(
      SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC,
      object : SnykFolderConfigListener {
        override fun folderConfigsChanged(hasConfigs: Boolean) {
          ApplicationManager.getApplication().invokeLater { severityToolbar.updateActionsAsync() }
        }
      },
    )

    connection.subscribe(
      SnykResultsFilteringListener.SNYK_FILTERING_TOPIC,
      object : SnykResultsFilteringListener {
        override fun filtersChanged() {
          ApplicationManager.getApplication().invokeLater { severityToolbar.updateActionsAsync() }
        }
      },
    )
  }

  private fun getSeverityToolbar(): ActionToolbar {
    val actionGroup = actionManager.getAction("io.snyk.plugin.TreeFilters.Severity") as ActionGroup
    return actionManager.createActionToolbar("Snyk Tree Toolbar", actionGroup, true)
  }
}
