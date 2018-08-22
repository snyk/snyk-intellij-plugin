package io.snyk.plugin.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{ActionManager, DataProvider, DefaultActionGroup}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.ui.state.SnykPluginState


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

  val ijLogger = com.intellij.openapi.diagnostic.Logger.getInstance(this.getClass)

  org.apache.log4j.Logger.getLogger(this.getClass).warn("test native log4j")
  org.slf4j.LoggerFactory.getLogger(this.getClass).warn("test native slf4j")
  val pluginState: SnykPluginState = SnykPluginState.forIntelliJ(project, this)

  val htmlPanel = new SnykHtmlPanel(project, pluginState)

  initialiseToolbar()
  setContent(htmlPanel)

  private[this] def initialiseToolbar(): Unit = {
    import io.snyk.plugin.ui.actions._

    val actionManager = ActionManager.getInstance()
    val resynkAction = new SnykRescanAction(pluginState)
    actionManager.registerAction("Snyk.Rescan", resynkAction)

    val toggleGroupDisplayAction = new SnykToggleGroupDisplayAction(pluginState)
    actionManager.registerAction("Snyk.ToggleGroupDisplay", toggleGroupDisplayAction)

    val selectProjectAction = new SnykSelectProjectAction(pluginState)
    actionManager.registerAction("Snyk.SelectProject", selectProjectAction)

    val actionGroup = new DefaultActionGroup(resynkAction, toggleGroupDisplayAction, selectProjectAction)
    actionManager.registerAction("Snyk.ActionsToolbar", actionGroup)

    val actionToolbar = actionManager.createActionToolbar("Snyk Toolbar", actionGroup, true)

    setToolbar(actionToolbar.getComponent)
  }

  override def dispose(): Unit = {
    val actionManager = ActionManager.getInstance()
    actionManager.unregisterAction("Snyk.Rescan")
    actionManager.unregisterAction("Snyk.ToggleGroupDisplay")
    actionManager.unregisterAction("Snyk.SelectProject")

    actionManager.unregisterAction("Snyk.ActionsToolbar")
  }
}

