package io.snyk.plugin.depsource

import com.intellij.openapi.project.Project
import monix.execution.Cancelable
import monix.reactive.{Observable, OverflowStrategy}

import scala.util.control.NonFatal
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListenerAdapter, ExternalSystemTaskType}
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import org.jetbrains.plugins.gradle.util.GradleConstants

object GradleProjectsObservable {
  private val overflowStrategy = OverflowStrategy.DropOld(10)

  def forProject(project: Project): Observable[Seq[String]] = Observable.create(overflowStrategy) { downstream =>
    try {
      val notificationManager = ServiceManager.getService(classOf[ExternalSystemProgressNotificationManager])

      notificationManager.addNotificationListener(new ExternalSystemTaskNotificationListenerAdapter() {
        override def onEnd(id: ExternalSystemTaskId): Unit = {
          if (id.getType == ExternalSystemTaskType.RESOLVE_PROJECT && id.getProjectSystemId == GradleConstants.SYSTEM_ID) {
            downstream.onNext(Seq(id.getIdeProjectId))
          }
        }
      })

      Cancelable.empty
    } catch {
      case NonFatal(ex) =>
        downstream.onError(ex)
        Cancelable.empty
    }
  }
}
