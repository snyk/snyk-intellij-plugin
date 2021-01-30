package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.services.SnykTaskQueueService

/**
 * Run scan project with Snyk action.
 */
class SnykRunScanAction : AnAction(AllIcons.Actions.Execute), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        actionEvent.presentation.isEnabled = false

        actionEvent.project!!.service<SnykTaskQueueService>().scan()

        actionEvent.presentation.isEnabled = true
    }

    override fun update(actionEvent: AnActionEvent) {
        val project = actionEvent.project
        if (project != null && !project.isDisposed) {
            actionEvent.presentation.isEnabled =
                !isScanRunning(project) && !getApplicationSettingsStateService().pluginFirstRun
        }
    }
}
