package io.snyk.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.LightColors
import com.intellij.ui.awt.RelativePoint
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getSnykCodeSettingsUrl
import java.awt.Point

class SnykBalloonNotifications {

    companion object {
        const val title = "Snyk"
        private const val groupNeedAction = "SnykNeedAction"
        private const val groupAutoHide = "SnykAutoHide"
        private val GROUP = NotificationGroup(groupNeedAction, NotificationDisplayType.STICKY_BALLOON)

        fun showError(message: String, project: Project, vararg actions: AnAction) =
            showNotification(message, project, NotificationType.ERROR, *actions)

        fun showInfo(message: String, project: Project, vararg actions: AnAction) =
            showNotification(message, project, NotificationType.INFORMATION, *actions)

        fun showWarn(message: String, project: Project, vararg actions: AnAction) =
            showNotification(message, project, NotificationType.WARNING, *actions)

        private fun showNotification(message: String, project: Project, type: NotificationType, vararg actions: AnAction) {
            val notification = if (actions.isEmpty()) {
                Notification(groupAutoHide, title, message, type)
            } else {
                GROUP.createNotification(title, message, type).apply {
                    actions.forEach { this.addAction(it) }
                }
            }
            notification.notify(project)
        }

        fun showSastForOrgEnablement(project: Project) = showInfo(
            "Snyk Code is disabled by your organisation's configuration. To enable navigate to ",
            project,
            NotificationAction.createSimpleExpiring("Snyk > Settings > Snyk Code") {
                BrowserUtil.browse(getSnykCodeSettingsUrl())
            }
        )

        fun showFeedbackRequest(project: Project) = showInfo(
            "Take part in Snyk's plugin research and get a \$100 Amazon gift card!",
            project,
            NotificationAction.createSimpleExpiring("Schedule user testing here") {
                getApplicationSettingsStateService().showFeedbackRequest = false
                BrowserUtil.browse("https://calendly.com/snyk-georgi/45min")
            },
            NotificationAction.createSimpleExpiring("Don’t show again") {
                getApplicationSettingsStateService().showFeedbackRequest = false
            }
        )

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
