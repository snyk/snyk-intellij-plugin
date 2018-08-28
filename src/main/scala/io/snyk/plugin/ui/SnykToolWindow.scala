package io.snyk.plugin.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{ActionGroup, ActionManager, DataProvider, DefaultActionGroup}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.{DumbAware, Project, ProjectManager}
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.SnykPluginProjectComponent
import io.snyk.plugin.ui.state.{Navigator, SnykPluginState}


/**
  * Top-level entry point to the plugin, as specified in `plugin.xml`.
  * Initialises the `SnykToolWindow` panel and registers it as content.
  */
class SnykToolWindowFactory extends ToolWindowFactory with DumbAware {
  override def createToolWindowContent(project: Project, toolWindow: ToolWindow): Unit = {
    val panel = new SnykToolWindow(project)
    val contentManager = toolWindow.getContentManager
    val content = contentManager.getFactory.createContent(panel, null, false)
    contentManager.addContent(content)
    Disposer.register(project, panel)
  }
}

class SnykToolWindow(project: Project) extends SimpleToolWindowPanel(true, true) with DataProvider with Disposable {
  this.setBackground(UIUtil.getPanelBackground)

  val projComp = project.getComponent(classOf[SnykPluginProjectComponent])
  import projComp.pluginState
  pluginState.navigator := Navigator.forIntelliJ(project, this, pluginState.idToMavenProject)

  val htmlPanel = new SnykHtmlPanel(project, pluginState)

  initialiseToolbar()
  setContent(htmlPanel)

  private[this] def initialiseToolbar(): Unit = {
    val actionManager = ActionManager.getInstance()
    val actionGroup = actionManager.getAction("io.snyk.plugin.ActionBar").asInstanceOf[ActionGroup]
    val actionToolbar = actionManager.createActionToolbar("Snyk Toolbar", actionGroup, true)

    setToolbar(actionToolbar.getComponent)
  }

  override def dispose(): Unit = {
  }
}

