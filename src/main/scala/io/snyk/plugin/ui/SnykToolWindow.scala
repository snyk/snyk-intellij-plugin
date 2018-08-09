package io.snyk.plugin.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.{ActionManager, DataProvider, DefaultActionGroup}
import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.{ToolWindow, ToolWindowFactory}
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.{MavenProject, MavenProjectsManager}
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.model.SnykPluginState
import javax.swing.JPanel
import java.awt.CardLayout

import monix.execution.Ack.Continue
import monix.execution.Scheduler.Implicits.global

import scala.concurrent.{Future, Promise}
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
  val pluginState: SnykPluginState = SnykPluginState.forIntelliJ(project, this)

  val htmlPanel = new SnykHtmlPanel(project, pluginState)
  val videoPanel = new SnykVideoPanel()

  val cardLayout = new CardLayout
  val cardPanel = new JPanel(cardLayout)
  cardPanel.add(htmlPanel, "html")
  cardPanel.add(videoPanel, "video")
  cardLayout.show(cardPanel, "html")

  initialiseToolbar()
  setContent(cardPanel)

  pluginState.mavenProjectsObservable subscribe { list =>
    println(s"updated projects: $list")
    Continue
  }

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
  /**
    * Use MavenProjectsManager to open the editor and highlight where the specified artifact
    * is imported.
    */
  def navToArtifact(group: String, name: String, mp: MavenProject): Future[Unit] = {
    val p = Promise[Unit]
    ApplicationManager.getApplication.invokeLater { () =>
      p complete Try {
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
    * and *only then* stop any video that may be playing and make the panel visible.
    */
  def navigateTo(path: String, params: ParamSet): Future[String] = {
    println(s"toolWindow navigateTo: $path $params")
    htmlPanel.navigateTo(path, params) map { resolvedUrl =>
      println(s"toolWindow navigateTo: $path completed")
      // Don't flip the card when scanning.
      // If needed, the asynk scan will take care of that when complete
      if(!pluginState.scanInProgress.get) {
        videoPanel.stop()
        cardLayout.show(cardPanel, "html")
      }
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
    println(s"toolWindow showVideo: $url")
    videoPanel.setSource(url)
    cardLayout.show(cardPanel, "video")
  }

  override def dispose(): Unit = {
    val actionManager = ActionManager.getInstance()
    actionManager.unregisterAction("Snyk.Rescan")
    actionManager.unregisterAction("Snyk.ToggleGroupDisplay")
    actionManager.unregisterAction("Snyk.SelectProject")

    actionManager.unregisterAction("Snyk.ActionsToolbar")
  }
}

