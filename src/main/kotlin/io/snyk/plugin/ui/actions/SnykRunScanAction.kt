package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.pluginSettings

/** Run scan project with Snyk action. */
class SnykRunScanAction : AnAction(AllIcons.Actions.Execute), DumbAware {

  override fun actionPerformed(actionEvent: AnActionEvent) {
    getSnykTaskQueueService(actionEvent.project!!)?.scan()
  }

  override fun update(actionEvent: AnActionEvent) {
    val project = actionEvent.project
    if (project != null && !project.isDisposed) {
      val settings = pluginSettings()
      actionEvent.presentation.isEnabled =
        !isCliDownloading() &&
          !isScanRunning(project) &&
          !settings.pluginFirstRun &&
          !settings.token.isNullOrEmpty()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
