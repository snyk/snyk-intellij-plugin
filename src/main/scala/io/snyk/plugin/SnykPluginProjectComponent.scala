package io.snyk.plugin

import com.intellij.diagnostic.ReportMessages
import com.intellij.notification.{NotificationListener, NotificationType}
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
    if (!pluginState.apiClient.isCLIInstalled()) {
      ReportMessages.GROUP.createNotification(
          "Warning",
        "Snyk CLI not installed. <a href=\"https://support.snyk.io/hc/en-us/articles/360003812458-Getting-started-with-the-CLI\">Please, install it</a>",
          NotificationType.WARNING,
          NotificationListener.URL_OPENING_LISTENER)
        .setImportant(false)
        .notify(project);
    }
  }

  override def projectClosed(): Unit = {
    SnykPluginState.removeForProject(project)
  }
}
