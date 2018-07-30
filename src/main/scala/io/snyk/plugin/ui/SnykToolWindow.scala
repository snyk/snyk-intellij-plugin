package io.snyk.plugin.ui

import com.intellij.execution.dashboard.actions.RunAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{ActionManager, ActionPlaces, DataProvider, DefaultActionGroup}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}
import com.intellij.util.ui.JBUI
import com.intellij.util.xml.ui.DomCollectionControl.RemoveAction
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.model.SnykPluginState
import javax.swing.JPanel
import java.awt.CardLayout

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.MavenProjectsManager

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import scala.util.Try

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
  val pluginState: SnykPluginState = SnykPluginState.forIntelliJ(this)

  val htmlPanel = new SnykHtmlPanel(project, pluginState)
  val videoPanel = new SnykVideoPanel()

  val cardLayout = new CardLayout
  val cardPanel = new JPanel(cardLayout)
  cardPanel.add(htmlPanel, "html")
  cardPanel.add(videoPanel, "video")
  cardLayout.show(cardPanel, "html")

  setToolbar(createToolbarPanel)
  setContent(cardPanel)

  /**
    * Use MavenProjectsManager to open the editor and highlight where the specified artifact
    * is imported.
    */
  def navToArtifact(group: String, name: String): Future[Unit] = {
    val p = Promise[Unit]
    ApplicationManager.getApplication.invokeLater { () =>
      p complete Try {
        val mp = MavenProjectsManager.getInstance(project).getProjects.get(0)
        val file = mp.getFile
        val artifact = mp.findDependencies(group, name).asScala.head
        val nav = MavenNavigationUtil.createNavigatableForDependency(project, file, artifact)
        nav.navigate(true)
      }
    }
    p.future
  }

  /**
    * Open the supplied path as a URL in the HTML panel.  Wait for navigation to be complete
    * then stop any video that may be playing and make the panel visible.
    */
  def navigateTo(path: String, params: ParamSet): Future[String] = {
    htmlPanel.navigateTo(path, params) map { resolvedUrl =>
      videoPanel.stop()
      cardLayout.show(cardPanel, "html")
      resolvedUrl
    }
  }

  /**
    * Open and start playing the requested video, makes the video panel visible
    * (hiding the HTML panel)
    * This is an unfortunate hack, and only necessary because the WebView in JavaFX 8
    * is unable to show either h.264 video or animated GIFs!
    */
  def showVideo(url: String): Unit = {
    videoPanel.setSource(url)
    cardLayout.show(cardPanel, "video")
  }

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

