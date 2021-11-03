package io.snyk.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import java.awt.Color
import java.awt.Component
import java.awt.Point
import javax.swing.Icon

object SnykBalloonNotificationHelper {

    private val logger = logger<SnykBalloonNotifications>()
    const val title = "Snyk"
    private const val groupNeedAction = "SnykNeedAction"
    private const val groupAutoHide = "SnykAutoHide"
    val GROUP = NotificationGroup(groupNeedAction, NotificationDisplayType.STICKY_BALLOON)

    fun showError(message: String, project: Project?, vararg actions: AnAction) {
        val foundProject = getProject(project)
        showNotification(message, foundProject, NotificationType.ERROR, *actions)
    }

    private fun getProject(project: Project?): Project? {
        var foundProject = project
        if (foundProject == null) {
            val openProjects: Array<Project> = ProjectManager.getInstance().openProjects
            if (openProjects.isNotEmpty()) {
                foundProject = openProjects[0]
            }
        }
        return foundProject
    }

    fun showError(message: String) = showNotification(message, null, NotificationType.ERROR)

    fun showInfo(message: String, project: Project, vararg actions: AnAction) =
        showNotification(message, project, NotificationType.INFORMATION, *actions)

    fun showWarn(message: String, project: Project, vararg actions: AnAction) =
        showNotification(message, project, NotificationType.WARNING, *actions)

    // TODO(pavel): refactor showNotification function to make it more generic + default arguments
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
        val notification = if (actions.isEmpty()) {
            Notification(groupAutoHide, title, message, type)
        } else {
            GROUP.createNotification(title, message, type).apply {
                actions.forEach { this.addAction(it) }
            }
        }
        notification.notify(project)
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
        val balloon = createBalloon(
            message,
            AllIcons.General.BalloonWarning,
            MessageType.WARNING.popupBackground
        )
        showBalloonForComponent(balloon, component, showAbove)
    }
}
