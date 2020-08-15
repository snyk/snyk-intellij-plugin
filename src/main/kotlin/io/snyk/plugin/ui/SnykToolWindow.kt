package io.snyk.plugin.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel

/**
 * IntelliJ ToolWindow for Snyk plugin.
 */
class SnykToolWindow(project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    init {
        val toolWindowPanel = project.service<SnykToolWindowPanel>()

        initialiseToolbar()
        setContent(toolWindowPanel)
    }

    private fun initialiseToolbar() {
        val actionManager = ActionManager.getInstance()
        val actionGroup = actionManager.getAction("io.snyk.plugin.ActionBar") as ActionGroup
        val actionToolbar = actionManager.createActionToolbar("Snyk Toolbar", actionGroup, true)

        toolbar = actionToolbar.component
    }

    override fun dispose() {
    }
}
