package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.pluginSettings

/**
 * Log out of Snyk. Delegates to [io.snyk.plugin.services.SnykCliAuthenticationService.logout].
 * Available via Find Action ("Snyk: Log out"), so users can recover from a stale/expired token
 * without needing the settings page to load first.
 */
class SnykLogoutAction : AnAction(AllIcons.Actions.Exit), DumbAware {

  override fun actionPerformed(actionEvent: AnActionEvent) {
    val project = actionEvent.project ?: return
    if (project.isDisposed) return
    getSnykCliAuthenticationService(project)?.logout()
  }

  override fun update(actionEvent: AnActionEvent) {
    val project = actionEvent.project
    actionEvent.presentation.isEnabled =
      project != null && !project.isDisposed && !pluginSettings().token.isNullOrEmpty()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
