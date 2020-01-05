package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import io.snyk.plugin.ui.state.SnykPluginState

object SnykPluginProjectComponent {
  def getInstance: SnykPluginProjectComponent =
    ApplicationManager.getApplication.getComponent(classOf[SnykPluginProjectComponent])
}

class SnykPluginProjectComponent(project: Project) extends ProjectComponent {
  val pluginState: SnykPluginState = SnykPluginState.forIntelliJ(project)

  override def initComponent() : Unit = {
  }

  override def disposeComponent() : Unit = {
  }

  override def projectOpened(): Unit = {
  }

  override def projectClosed(): Unit = {
    SnykPluginState.removeForProject(project)
  }
}
