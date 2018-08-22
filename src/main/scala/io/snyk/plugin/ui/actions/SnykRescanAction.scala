package io.snyk.plugin
package ui.actions


import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.ui.state.SnykPluginState
import monix.execution.Scheduler.Implicits.global

class SnykRescanAction(pluginState: SnykPluginState)
extends AnAction("Re-Scan project with Snyk", null, AllIcons.Actions.Refresh)
with DumbAware
with IntellijLogging {

  import pluginState.{performScan, navigator}

  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    val p = e.getPresentation
    p.setEnabled(true)
    p.setVisible(true)
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    log.debug("Rescan button clicked")

//    log.debug(s"*** SOURCE SETS ***")
//    pluginState.externProj.gradleSourceSets foreach { ss => log.debug(ss.toMultiLineString) }

    navigator.navToScanning()
    performScan(force=true) andThen { case _ => navigator.navToVulns() }
  }
}
