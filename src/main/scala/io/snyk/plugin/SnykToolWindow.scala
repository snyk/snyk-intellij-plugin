package io.snyk.plugin

import com.intellij.execution.dashboard.actions.RunAction
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.actions.{CollapseAllAction, ExpandAllAction}
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{ActionManager, ActionPlaces, DataProvider, DefaultActionGroup}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}
import com.intellij.util.xml.ui.DomCollectionControl.{AddAction, RemoveAction}
import com.intellij.util.ui.JBUI

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

  import javax.swing.JPanel

  setToolbar(createToolbarPanel)
  setContent(new SnykHtmlPanel(project))

  private def createToolbarPanel: JPanel = {
    val group = new DefaultActionGroup()

    group.add(new RemoveAction())
    group.add(new RunAction())

//    var action = CommonActionsManager.getInstance.createExpandAllAction(myTreeExpander, this)
//    action.getTemplatePresentation.setDescription(AntBundle.message("ant.explorer.expand.all.nodes.action.description"))
//    group.add(action)
//
//    action = CommonActionsManager.getInstance.createCollapseAllAction(myTreeExpander, this)
//    action.getTemplatePresentation.setDescription(AntBundle.message("ant.explorer.collapse.all.nodes.action.description"))
//    group.add(action)
//
//    group.add(myAntBuildFilePropertiesAction)

    val actionToolBar = ActionManager.getInstance.createActionToolbar(ActionPlaces.ANT_EXPLORER_TOOLBAR, group, true)

    JBUI.Panels.simplePanel(actionToolBar.getComponent)
  }
  override def dispose(): Unit = {

  }
}

