package io.snyk.plugin
package ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.ui.state.SnykPluginState
import monix.execution.Scheduler.Implicits.global

class SnykRescanAction() extends AnAction(AllIcons.Actions.Refresh)
  with DumbAware
  with IntellijLogging {

  override def update(e: AnActionEvent): Unit = {
    super.update(e)

    val presentation = e.getPresentation

    presentation.setEnabled(true)
    presentation.setVisible(true)
  }

  override def actionPerformed(event: AnActionEvent): Unit = {
    log.debug("Rescan button clicked")

    def pluginState = SnykPluginState.getInstance(event.getProject)
    def navigator = pluginState.navigator()

    navigator.navToScanning()
    pluginState.performScan(force=true) andThen { case _ => navigator.navToVulns() }
  }
}
