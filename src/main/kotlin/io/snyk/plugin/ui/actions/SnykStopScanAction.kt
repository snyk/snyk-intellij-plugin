package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.isScanRunning

/**
 * Stop scan project with Snyk action.
 */
class SnykStopScanAction : AnAction(AllIcons.Actions.Suspend), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        getSnykTaskQueueService(actionEvent.project!!)?.stopScan()
    }

    override fun update(actionEvent: AnActionEvent) {
        val project = actionEvent.project
        if (project != null && !project.isDisposed) {
            actionEvent.presentation.isEnabled = isScanRunning(project)
        }
    }
}
