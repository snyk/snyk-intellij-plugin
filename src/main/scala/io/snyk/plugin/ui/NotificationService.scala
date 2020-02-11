package io.snyk.plugin.ui

import com.intellij.diagnostic.ReportMessages
import com.intellij.notification.{NotificationListener, NotificationType}
import com.intellij.openapi.project.Project

object NotificationService {
  def showWarning(project: Project, message: String): Unit = {
    ReportMessages.GROUP.createNotification(
      "Warning",
      message,
      NotificationType.WARNING,
      NotificationListener.URL_OPENING_LISTENER)
      .setImportant(false)
      .notify(project);
  }
}
