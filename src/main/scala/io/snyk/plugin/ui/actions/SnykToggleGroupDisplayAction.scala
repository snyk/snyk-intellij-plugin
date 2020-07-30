package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import io.snyk.plugin.ui.state.{Flag, SnykPluginState}

class SnykToggleGroupDisplayAction()
extends ToggleAction("Toggle display of Maven Groups", null, AllIcons.General.HideToolWindow) {
  override def update(event: AnActionEvent): Unit = {
    super.update(event)
    val presentation = event.getPresentation
    presentation.setEnabled(true)
    presentation.setVisible(true)
  }

  override def isSelected(event: AnActionEvent): Boolean = {
    def pluginState = SnykPluginState.getInstance(event.getProject)

    pluginState.flags(Flag.HideMavenGroups)
  }

  override def setSelected(event: AnActionEvent, state: Boolean): Unit = {
    def pluginState = SnykPluginState.getInstance(event.getProject)
    def navigator = pluginState.navigator()

    pluginState.flags(Flag.HideMavenGroups) = state
    navigator.reloadWebView()
  }
}
