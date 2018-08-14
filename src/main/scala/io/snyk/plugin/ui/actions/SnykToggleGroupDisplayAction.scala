package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import io.snyk.plugin.ui.state.{Flag, SnykPluginState}

class SnykToggleGroupDisplayAction(pluginState: SnykPluginState)
extends ToggleAction("Toggle display of Maven Groups", null, AllIcons.General.HideDownPart) {
  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    val p = e.getPresentation
    p.setEnabled(true)
    p.setVisible(true)
  }

  override def isSelected(e: AnActionEvent): Boolean = pluginState.flags(Flag.HideMavenGroups)

  override def setSelected(e: AnActionEvent, state: Boolean): Unit = {
    pluginState.flags(Flag.HideMavenGroups) = state
    pluginState.reloadWebView()
  }
}
