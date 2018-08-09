package io.snyk.plugin.ui.actions



import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.model.SnykPluginState
import scala.concurrent.ExecutionContext.Implicits.global

class SnykRescanAction(pluginState: SnykPluginState)
extends AnAction("Re-Scan project with Snyk", null, AllIcons.Actions.Refresh)
with DumbAware {
  override def update(e: AnActionEvent): Unit = {
    super.update(e)
    val p = e.getPresentation
    p.setEnabled(true)
    p.setVisible(true)
  }

  override def actionPerformed(e: AnActionEvent): Unit = {
    pluginState.performScan(force=true) andThen { case _ =>
      pluginState.navigateTo("/html/vulns.hbs", ParamSet.Empty)
    }
    println("Rescan button clicked")
  }
}
