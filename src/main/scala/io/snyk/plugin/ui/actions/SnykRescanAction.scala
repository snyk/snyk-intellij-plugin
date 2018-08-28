package io.snyk.plugin
package ui.actions


import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.project.DumbAware
import monix.execution.Scheduler.Implicits.global

class SnykRescanAction()
extends AnAction(AllIcons.Actions.Refresh)
with DumbAware
with IntellijLogging {
  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    val p = e.getPresentation
    p.setEnabled(true)
    p.setVisible(true)
  }



  override def actionPerformed(e: AnActionEvent): Unit = {
    log.debug("Rescan button clicked")
    val projComp = e.getProject.getComponent(classOf[SnykPluginProjectComponent])
    def pluginState = projComp.pluginState
    def navigator = pluginState.navigator()

//    log.debug(s"*** SOURCE SETS ***")
//    pluginState.externProj.gradleSourceSets foreach { ss => log.debug(ss.toMultiLineString) }

    navigator.navToScanning()
    pluginState.performScan(force=true) andThen { case _ => navigator.navToVulns() }
  }
}
