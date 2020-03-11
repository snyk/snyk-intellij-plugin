package io.snyk.plugin.depsource

import com.intellij.openapi.project.Project
import monix.execution.Cancelable
import monix.reactive.{Observable, OverflowStrategy}
import org.jetbrains.idea.maven.project.{MavenProject, MavenProjectChanges, MavenProjectsManager, MavenProjectsTree}

import scala.util.control.NonFatal
import scala.collection.JavaConverters._

object MavenProjectsObservable {
  val overflowStrategy = OverflowStrategy.DropOld(10)

  def forProject(project: Project): Observable[Seq[MavenProject]] = Observable.create(overflowStrategy){ downstream =>
    try {
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)

      mavenProjectsManager.addManagerListener(new MavenProjectsManager.Listener {
        override def importAndResolveScheduled(): Unit = {
          val mavenProjects: Seq[MavenProject]  = mavenProjectsManager.getProjects.asScala

          downstream.onNext(mavenProjects)
        }
      })
      Cancelable.empty
    } catch { case NonFatal(ex) =>
      downstream.onError(ex)
      Cancelable.empty
    }
  }
}
