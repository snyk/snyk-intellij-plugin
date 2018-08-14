package io.snyk.plugin

import com.intellij.openapi.project.Project
import io.circe.{Json, JsonObject}
import io.snyk.plugin.datamodel.SnykMavenArtifact
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.concurrent.Worker
import org.jetbrains.idea.maven.project.MavenProjectsManager

import scala.concurrent.{Future, Promise, duration}
import scala.concurrent.duration.Duration
import javafx.util.{Duration => JfxDuration}

import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

object Implicits {

  implicit class RichProject(val p: Project) extends AnyVal {
    def toDepNode: SnykMavenArtifact = {
      val allMavenProjects = MavenProjectsManager.getInstance(p).getProjects.asScala
      val mp = allMavenProjects.head
      allMavenProjects.foreach { p =>
        println(s"Enumerating maven project $p")
      }

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

  implicit class RichOption[T](val opt: Option[T]) extends AnyVal {
    def toTryOr(errMsg: String): Try[T] = opt match {
      case Some(o) => Success.apply(o)
      case None => Failure(new RuntimeException(errMsg))
    }
  }

  implicit class RichJson(val j: Json) extends AnyVal {
    def tryAsObject: Try[JsonObject] = j.asObject.toTryOr(s"config json is not an object: ${j.toString}")
    def tryToString: Try[String] = j.asString.toTryOr(s"json value is not a string: ${j.toString}")
  }

  implicit class RichJsonObject(val j: JsonObject) extends AnyVal {
    def tryGet(key: String): Try[Json] = j(key).toTryOr(s"key [$key] not present in JSON object: ${j.toString}")
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
