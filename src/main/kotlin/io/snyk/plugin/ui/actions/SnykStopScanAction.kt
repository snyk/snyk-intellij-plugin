package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.services.SnykTaskQueueService

/**
 * Stop scan project with Snyk action.
 */
class SnykStopScanAction : AnAction(AllIcons.Actions.Suspend), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        val taskQueueService = actionEvent.project!!.service<SnykTaskQueueService>()

        taskQueueService.stopScan()
    }

    override fun update(actionEvent: AnActionEvent) {
        val project = actionEvent.project
        if (project != null && !project.isDisposed) {
            actionEvent.presentation.isEnabled = isScanRunning(project)
        }
    }
}
