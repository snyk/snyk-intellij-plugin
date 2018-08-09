package io.snyk.plugin.model

import org.jetbrains.idea.maven.project.{MavenProject, MavenProjectChanges, MavenProjectsManager, MavenProjectsTree}

import scala.collection.JavaConverters._
import java.{util => ju}

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Pair => IJPair}
import monix.execution.Cancelable
import monix.reactive.{Observable, OverflowStrategy}

import scala.util.control.NonFatal


object MavenProjectsObservable {
  val os = OverflowStrategy.DropOld(10)
  def forProject(project: Project): Observable[Seq[MavenProject]] = Observable.create(os){ downstream =>
    try {
      val projMgr = MavenProjectsManager.getInstance(project)
      projMgr.addProjectsTreeListener(new MavenProjectsTree.Listener {
        override def projectsUpdated(
          updated: ju.List[IJPair[MavenProject, MavenProjectChanges]],
          deleted: ju.List[MavenProject]
        ): Unit = downstream.onNext(updated.asScala.map(_.first))
      })
      Cancelable.empty
    } catch { case NonFatal(ex) =>
      downstream.onError(ex)
      Cancelable.empty
    }
  }

}
