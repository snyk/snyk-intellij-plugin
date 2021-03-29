package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel

/**
 * Clean scan results (UI and caches) for the project.
 */
class SnykCleanScanAction : AnAction(AllIcons.Actions.GC), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        actionEvent.project!!.service<SnykToolWindowPanel>().cleanUiAndCaches()
    }

    override fun update(actionEvent: AnActionEvent) {
        val project = actionEvent.project
        actionEvent.presentation.isEnabled = project != null && !project.isDisposed
    }
}
