package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel

/**
 * Run scan project with Snyk action.
 */
class SnykCleanScanAction : AnAction(AllIcons.Actions.Close), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        actionEvent.project!!.service<SnykToolWindowPanel>().clean()
    }

    override fun update(actionEvent: AnActionEvent) {
        actionEvent.presentation.isEnabled = !actionEvent.project!!.service<SnykToolWindowPanel>().isEmpty()
    }
}
