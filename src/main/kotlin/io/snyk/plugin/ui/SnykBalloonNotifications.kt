package io.snyk.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.LightColors
import com.intellij.ui.awt.RelativePoint
import java.awt.Point

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

        fun showWarnBalloonAtEventPlace(message: String, e: AnActionEvent) {
            val balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
                message,
                AllIcons.General.BalloonWarning,
                LightColors.YELLOW,
                null
            )
                .setHideOnClickOutside(true)
                .setFadeoutTime(5000)
                .createBalloon()

            val component = e.inputEvent?.component ?: e.getData(CONTEXT_COMPONENT)

            if (component != null) {
                balloon.show(
                    RelativePoint(component, Point(component.width / 2, component.height)),
                    Balloon.Position.below
                )
            }
            //todo: case if no Component exist (action invoked from Search?)
        }
    }
}
