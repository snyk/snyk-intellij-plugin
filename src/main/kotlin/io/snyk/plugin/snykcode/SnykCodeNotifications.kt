package io.snyk.plugin.snykcode

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

class SnykCodeNotifications {

    init {
        NotificationGroup(
            groupNeedAction, NotificationDisplayType.STICKY_BALLOON, true, "SnykCode")
    }

    companion object {
        const val title = "SnykCode"
        const val groupNeedAction = "SnykCodeNeedAction"
        private const val groupAutoHide = "SnykCodeAutoHide"

        fun showError(message: String, project: Project) {
            Notification(groupAutoHide, title, message, NotificationType.ERROR).notify(project)
        }

        fun showInfo(message: String, project: Project) {
            Notification(groupAutoHide, title, message, NotificationType.INFORMATION).notify(project)
        }

        fun showWarn(message: String, project: Project) {
            Notification(groupAutoHide, title, message, NotificationType.WARNING).notify(project)
        }

    }

}
