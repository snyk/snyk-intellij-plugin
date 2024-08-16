package io.snyk.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.util.progress.sleepCancellable
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.snykToolWindow
import org.jetbrains.concurrency.runAsync
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.toSnykCodeSettingsUrl
import java.awt.event.MouseEvent

object SnykBalloonNotifications {
    private val logger = logger<SnykBalloonNotifications>()

    const val sastForOrgEnablementMessage = "Snyk Code is disabled by your organisation's configuration."
    const val networkErrorAlertMessage = "Not able to connect to Snyk server."

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

    fun showNetworkErrorAlert(project: Project) = SnykBalloonNotificationHelper.showError(
        "$networkErrorAlertMessage Check connection and network settings.",
        project,
        NotificationAction.createSimpleExpiring("SNYK SETTINGS") {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, SnykProjectSettingsConfigurable::class.java)
        }
    )

    fun showAdvisorMoreDetailsPopup(htmlMessage: String, mouseEvent: MouseEvent): Balloon {
        val balloon = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
            htmlMessage,
            null,
            MessageType.WARNING.popupBackground,
            BrowserHyperlinkListener.INSTANCE
        )
            .setHideOnClickOutside(true)
            .setAnimationCycle(300)
            .createBalloon()

        SnykBalloonNotificationHelper.showBalloonForComponent(balloon, mouseEvent.component, true, mouseEvent.point)

        return balloon
    }
}
