package io.snyk.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.ui.awt.RelativePoint
import icons.SnykIcons
import java.awt.Color
import java.awt.Component
import java.awt.Point
import javax.swing.Icon

object SnykBalloonNotificationHelper {

    private val logger = logger<SnykBalloonNotifications>()
    const val TITLE = "Snyk"
    private const val GROUP_AUTO_HIDE = "SnykAutoHide"

    fun showError(message: String, project: Project?, vararg actions: AnAction) {
        showNotification(message, project, NotificationType.ERROR, *actions)
    }

    fun showInfo(message: String, project: Project?, vararg actions: AnAction) =
        showNotification(message, project, NotificationType.INFORMATION, *actions)

    fun showWarn(message: String, project: Project?, vararg actions: AnAction) =
        showNotification(message, project, NotificationType.WARNING, *actions)

    private fun showNotification(
        message: String,
        project: Project?,
        type: NotificationType,
        vararg actions: AnAction
    ): Notification {
        when (type) {
            NotificationType.ERROR, NotificationType.WARNING -> logger.warn(message)
            else -> logger.info(message)
        }
        val msg = message.replace("\n","<br/>")
        val notification = if (actions.isEmpty()) {
            Notification(GROUP_AUTO_HIDE, TITLE, msg, type)
        } else {
            notificationGroup.createNotification(TITLE, msg, type).apply {
                actions.forEach { this.addAction(it) }
            }
        }

        notification.icon = SnykIcons.TOOL_WINDOW
        // workaround for https://youtrack.jetbrains.com/issue/IDEA-220408/notifications-with-project=null-is-not-shown-anymore
        if (project != null) {
            notification.notify(project)
        } else {
            ProjectUtil.getActiveProject()?.let { notification.notify(it) }
        }
        return notification
    }

    private fun createBalloon(message: String, icon: Icon, color: Color): Balloon =
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, icon, color, null)
            .setHideOnClickOutside(true)
            .setFadeoutTime(5000)
            .createBalloon()

    fun showBalloonForComponent(
        balloon: Balloon,
        component: Component,
        showAbove: Boolean,
        point: Point? = null
    ) {
        balloon.show(
            RelativePoint(
                component,
                point ?: Point(component.width / 2, if (showAbove) 0 else component.height)
            ),
            if (showAbove) Balloon.Position.above else Balloon.Position.below
        )
    }

    fun showInfoBalloonForComponent(message: String, component: Component, showAbove: Boolean = false): Balloon {
        return createBalloon(
            message,
            AllIcons.General.BalloonInformation,
            MessageType.INFO.popupBackground
        ).apply {
            showBalloonForComponent(this, component, showAbove)
        }
    }

    fun showWarnBalloonAtEventPlace(message: String, e: AnActionEvent, showAbove: Boolean = false) {
        val component = e.inputEvent?.component ?: e.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return
        // todo: case if no Component exist (action invoked from Search?)
        showWarnBalloonForComponent(message, component, showAbove)
    }

    fun showWarnBalloonForComponent(message: String, component: Component, showAbove: Boolean = false) {
        val balloon = createBalloon(
            message,
            AllIcons.General.BalloonWarning,
            MessageType.WARNING.popupBackground
        )
        showBalloonForComponent(balloon, component, showAbove)
    }
}
