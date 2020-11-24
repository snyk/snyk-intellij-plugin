package io.snyk.plugin.snykcode

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.snyk.plugin.snykcode.core.SCLogger

class SnykCodeNotifications {

    init {
        NotificationGroup(
            groupNeedAction, NotificationDisplayType.STICKY_BALLOON, true, SCLogger.presentableName)
    }

    companion object {
        const val title = SCLogger.presentableName
        const val groupNeedAction = "${SCLogger.presentableName}NeedAction"
        private const val groupAutoHide = "${SCLogger.presentableName}AutoHide"

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
