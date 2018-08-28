package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent

object SnykPluginAppComponent {
  def getInstance: SnykPluginAppComponent =
    ApplicationManager.getApplication.getComponent(classOf[SnykPluginAppComponent])
}

class SnykPluginAppComponent extends ApplicationComponent {
  override def initComponent() : Unit = {
  }

  override def disposeComponent() : Unit = {
  }
}
