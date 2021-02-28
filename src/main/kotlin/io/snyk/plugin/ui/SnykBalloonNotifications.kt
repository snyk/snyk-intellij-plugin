package io.snyk.plugin.ui

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project

class SnykBalloonNotifications {

    companion object {
        const val title = "Snyk"
        private const val groupNeedAction = "SnykNeedAction"
        private const val groupAutoHide = "SnykAutoHide"
        private val GROUP = NotificationGroup(groupNeedAction, NotificationDisplayType.STICKY_BALLOON)

        fun showError(message: String, project: Project, action: AnAction? = null) {
            val notification = if (action == null) {
                Notification(groupAutoHide, title, message, NotificationType.ERROR)
            } else {
                GROUP.createNotification(title, message, NotificationType.ERROR).addAction(action)
            }
            notification.notify(project)
        }

        fun showInfo(message: String, project: Project) {
            Notification(groupAutoHide, title, message, NotificationType.INFORMATION).notify(project)
        }

        fun showWarn(message: String, project: Project) {
            Notification(groupAutoHide, title, message, NotificationType.WARNING).notify(project)
        }
    }
}
