package io.snyk.plugin.snykcode

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import io.snyk.plugin.snykcode.core.SCLogger

class SnykCodeNotifications {

    companion object {
        const val title = SCLogger.presentableName
        private const val groupNeedAction = "${SCLogger.presentableName}NeedAction"
        private const val groupAutoHide = "${SCLogger.presentableName}AutoHide"
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
