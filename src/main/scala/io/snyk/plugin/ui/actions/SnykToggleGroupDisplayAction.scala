package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnActionEvent, ToggleAction}
import io.snyk.plugin.SnykPluginProjectComponent
import io.snyk.plugin.ui.state.Flag

class SnykToggleGroupDisplayAction()
extends ToggleAction("Toggle display of Maven Groups", null, AllIcons.General.HideToolWindow) {
  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    val p = e.getPresentation
    p.setEnabled(true)
    p.setVisible(true)
  }

  override def isSelected(e: AnActionEvent): Boolean = {
    val projComp = e.getProject.getComponent(classOf[SnykPluginProjectComponent])
    def pluginState = projComp.pluginState

    pluginState.flags(Flag.HideMavenGroups)
  }

  override def setSelected(e: AnActionEvent, state: Boolean): Unit = {
    val projComp = e.getProject.getComponent(classOf[SnykPluginProjectComponent])
    def pluginState = projComp.pluginState
    def navigator = pluginState.navigator()

    pluginState.flags(Flag.HideMavenGroups) = state
    navigator.reloadWebView()
  }
}
