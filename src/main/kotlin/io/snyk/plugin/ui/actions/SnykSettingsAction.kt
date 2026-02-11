package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware

/** Show Snyk settings panel action. */
class SnykSettingsAction : AnAction(AllIcons.General.Settings), DumbAware {

  override fun actionPerformed(actionEvent: AnActionEvent) {
    val project = actionEvent.project ?: return
    if (project.isDisposed) return

    ApplicationManager.getApplication().invokeLater {
      if (project.isDisposed) return@invokeLater
      ShowSettingsUtil.getInstance()
        .showSettingsDialog(project, "io.snyk.plugin.settings.SnykProjectSettingsConfigurable")
    }
  }

  override fun update(actionEvent: AnActionEvent) {
    actionEvent.presentation.isEnabled =
      actionEvent.project != null && !actionEvent.project!!.isDisposed
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
