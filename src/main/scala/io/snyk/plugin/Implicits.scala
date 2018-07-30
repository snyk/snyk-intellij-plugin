package io.snyk.plugin

import com.intellij.openapi.project.Project
import io.snyk.plugin.model.SnykMavenArtifact
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.concurrent.Worker
import org.jetbrains.idea.maven.project.MavenProjectsManager

import scala.concurrent.{Future, Promise, duration}
import scala.concurrent.duration.Duration
import javafx.util.{Duration => JfxDuration}
object Implicits {

  implicit class RichProject(val p: Project) extends AnyVal {
    def toDepNode: SnykMavenArtifact = {
      val mp = MavenProjectsManager.getInstance(p).getProjects.get(0)
      SnykMavenArtifact.fromMavenProject(mp)
    }
  }

  implicit class RichObservableValue[T](val ov: ObservableValue[T]) extends AnyVal {
    def oneShot(condition: T => Boolean)(callback: T => Unit): Unit = {
      lazy val listener = new ChangeListener[T] { thisListener =>
        override def changed(obs: ObservableValue[_ <: T], oldV: T, newV: T): Unit = {
          if(condition(newV)) {
            callback(newV)
            obs.removeListener(this)
          }
        }
      }
      ov.addListener(listener)
    }

    def futureOneShot(condition: T => Boolean): Future[T] = {
      val p = Promise[T]
      oneShot(condition)(p.success)
      p.future
    }
  }

  implicit class RichJfxWorker(val w: Worker[_]) extends AnyVal {
    def onNextSucceeded(callback: => Unit): Unit =
      w.stateProperty.oneShot(_ == Worker.State.SUCCEEDED)(_ => callback)
  }

  object DurationConverters {
    implicit class RichScalaDuration(val d: Duration) extends AnyVal {
      def asFx: JfxDuration = d match {
        case Duration.Undefined => JfxDuration.UNKNOWN
        case Duration.Inf => JfxDuration.INDEFINITE
        case _ => new JfxDuration(d.toMillis)

      }
    }

    implicit class RichJfxDuration(val d: JfxDuration) extends AnyVal {
      def asScala: Duration = {
        if (d.isIndefinite) Duration.Inf
        else if (d.isUnknown) Duration.Undefined
        else Duration(d.toMillis, duration.MILLISECONDS)
      }
    }

  }

}
