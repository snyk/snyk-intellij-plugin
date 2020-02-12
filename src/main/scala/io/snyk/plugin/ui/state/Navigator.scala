package io.snyk.plugin
package ui.state

import java.util.ArrayList

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.pom.NavigatableAdapter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiManager, PsiRecursiveElementWalkingVisitor}
import io.snyk.plugin.depsource.{BuildToolProject, MavenBuildToolProject, ProjectType}
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.ui.SnykToolWindow
import org.jetbrains.idea.maven.navigator.MavenNavigationUtil

import scala.concurrent.{Future, Promise}
import scala.util.Try
import scala.collection.JavaConverters._
import monix.execution.Scheduler.Implicits.global

trait Navigator {
  def navigateTo(path: String, params: ParamSet): Future[String]
  def navigateToDependency(group: String, name: String, projectId: String): Future[Unit]

  def navToVulns(): Future[String] = navigateTo("/vulnerabilities", ParamSet.Empty)
  def navToScanning(): Future[String] = navigateTo("/scanning", ParamSet.Empty)

  def reloadWebView(): Unit
}

object Navigator extends IntellijLogging {

  private val DEPENDENCIES_KEY = "dependencies"

  def newInstance(project: Project,
                  toolWindow: SnykToolWindow,
                  idToProject: String => Option[BuildToolProject]
                 ): Navigator = new IntellijNavigator(project, toolWindow, idToProject)

  def mock: Navigator = MockNavigator

  class IntellijNavigator(project: Project,
                          toolWindow: SnykToolWindow,
                          idToProject: String => Option[BuildToolProject]) extends Navigator {

    override def navigateTo(path: String, params: ParamSet): Future[String] = {
      val p = Promise[String]
      ApplicationManager.getApplication.invokeLater { () =>
        p completeWith {
          toolWindow.htmlPanel.navigateTo(path, params) map { resolvedUrl =>
            log.info(s"navigateTo: $path completed")
            resolvedUrl
          }
        }
      }
      p.future
    }

    /**
      * Use MavenProjectsManager to open the editor and highlight where the specified artifact
      * is imported.
      */
    override def navigateToDependency(
      group: String,
      name: String,
      projectId: String
    ): Future[Unit] = idToProject(projectId) map { buildToolProject =>
      val promisedUnit = Promise[Unit]

      ApplicationManager.getApplication.invokeLater { () =>
        log.info(s"Navigating to Artifact: $group : $name in $projectId")

        promisedUnit complete Try {
          buildToolProject.getType match {
            case ProjectType.MAVEN => openMavenDependency(group, name, buildToolProject)
            case ProjectType.GRADLE => openGradleDependency(group, name, buildToolProject)
          }
        }
      }

      promisedUnit.future
    } getOrElse Future.successful(())

    override def reloadWebView(): Unit = toolWindow.htmlPanel.reload()

    private[this] def openMavenDependency(group: String, name: String, buildToolProject: BuildToolProject): Unit = {
      val file = buildToolProject.getFile

      log.debug(s"  file: $file")

      val artifact = buildToolProject.asInstanceOf[MavenBuildToolProject].findDependencies(group, name).asScala.head

      log.debug(s"  artifact: $artifact")

      val navigatable = MavenNavigationUtil.createNavigatableForDependency(project, file, artifact)

      navigatable.navigate(true)
    }

    private[this] def openGradleDependency(group: String, name: String, buildToolProject: BuildToolProject): Unit = {
      val gradleBuildVirtualFile = buildToolProject.getFile

      val psiFile = PsiManager.getInstance(project).findFile(gradleBuildVirtualFile)

      val dependenciesPsiElement = PsiTreeUtil
        .getChildrenOfType(psiFile, classOf[PsiElement])
        .filter(_.getText.startsWith(DEPENDENCIES_KEY))

      val navigateToDependencyPsiElements = new ArrayList[PsiElement]

      dependenciesPsiElement.head.accept(new PsiRecursiveElementWalkingVisitor() {
        override def visitElement(element: PsiElement): Unit = {
          val elementText = element.getText

          if (!elementText.startsWith(DEPENDENCIES_KEY) && !elementText.contains("\n")
              && elementText.contains(group) && elementText.contains(name)) {

            navigateToDependencyPsiElements.add(element)
          }

          super.visitElement(element)
        }
      })

      if (!navigateToDependencyPsiElements.isEmpty) {
        NavigatableAdapter
          .navigate(project, gradleBuildVirtualFile, navigateToDependencyPsiElements.get(0).getTextRange.getStartOffset, true)
      }
    }
  }

  object MockNavigator extends Navigator {
    override def navigateTo(path: String, params: ParamSet): Future[String] = {
      log.info(s"MockSnykPluginState.navigateTo($path, $params)")
      Future.successful(path)
    }

    override def navigateToDependency(group: String, name: String, projectId: String): Future[Unit] = {
      log.info(s"MockSnykPluginState.navToArtifact($group, $name)")
      Future.successful(())
    }

    override def reloadWebView(): Unit = ()
  }
}
