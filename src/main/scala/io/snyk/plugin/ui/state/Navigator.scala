package io.snyk.plugin.ui.state

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.ui.SnykToolWindow
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil
import org.jetbrains.idea.maven.project.MavenProject

import scala.concurrent.{Future, Promise}
import scala.util.Try
import scala.collection.JavaConverters._

trait Navigator {
  def navigateTo(path: String, params: ParamSet): Future[String]
  def showVideo(url: String): Unit
  def navToArtifact(group: String, name: String, projectId: String): Future[Unit]
}

object Navigator {

  class IntellijNavigator(
    project: Project,
    toolWindow: SnykToolWindow,
    idToProject: String => Option[MavenProject]
  ) extends Navigator {
    override def navigateTo(path: String, params: ParamSet): Future[String] =
      toolWindow.navigateTo(path, params)

    override def showVideo(url: String): Unit = toolWindow.showVideo(url)

    /**
      * Use MavenProjectsManager to open the editor and highlight where the specified artifact
      * is imported.
      */
    override def navToArtifact(
      group: String,
      name: String,
      projectId: String
    ): Future[Unit] = idToProject(projectId) map { mp =>
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
    } getOrElse Future.successful(())
  }

  class MockNavigator extends Navigator {
    override def navigateTo(path: String, params: ParamSet): Future[String] = {
      println(s"MockSnykPluginState.navigateTo($path, $params)")
      Future.successful(path)
    }

    override def showVideo(url: String): Unit =
      println(s"MockSnykPluginState.showVideo($url)")

    override def navToArtifact(group: String, name: String, projectId: String): Future[Unit] = {
      println(s"MockSnykPluginState.navToArtifact($group, $name)")
      Future.successful(())
    }
  }
}
