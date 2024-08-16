package io.snyk.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykToolWindow

object SnykBalloonNotifications {
    private val logger = logger<SnykBalloonNotifications>()

    private val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Snyk")

    fun showWelcomeNotification(project: Project) {
        val welcomeMessage = "Welcome to Snyk! Check out our tool window to start analyzing your code"
        logger.info(welcomeMessage)
        val notification = notificationGroup.createNotification(
            welcomeMessage,
            NotificationType.INFORMATION
        ).addAction(
            NotificationAction.createSimpleExpiring("CONFIGURE SNYK\u2026") {
                snykToolWindow(project)?.show()
            }
        )
        notification.notify(project)
    }

    fun showFeedbackRequest(project: Project) = SnykBalloonNotificationHelper.showInfo(
        "<html>Have any ideas or feedback for us? <br>Get in touch with us.</html>",
        project,
        NotificationAction.createSimpleExpiring("Get in touch") {
            pluginSettings().showFeedbackRequest = false
            BrowserUtil.browse("https://snyk.io/contact-us/?utm_source=JETBRAINS_IDE")
        },
        NotificationAction.createSimpleExpiring("Don’t show again") {
            pluginSettings().showFeedbackRequest = false
        }
    )
}
