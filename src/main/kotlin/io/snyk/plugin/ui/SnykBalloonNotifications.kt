package io.snyk.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import io.snyk.plugin.analytics.getSelectedProducts
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.getSnykCodeSettingsUrl
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.snykToolWindow
import io.snyk.plugin.startSastEnablementCheckLoop
import snyk.analytics.AnalysisIsTriggered
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon

class SnykBalloonNotifications {

    companion object {
        private val logger = logger<SnykBalloonNotifications>()
        const val title = "Snyk"
        private const val groupNeedAction = "SnykNeedAction"
        private const val groupAutoHide = "SnykAutoHide"
        private val GROUP = NotificationGroup(groupNeedAction, NotificationDisplayType.STICKY_BALLOON)

        const val sastForOrgEnablementMessage = "Snyk Code is disabled by your organisation's configuration."
        const val networkErrorAlertMessage = "Not able to connect to Snyk server."

        private val alarm = Alarm()

        fun showError(message: String, project: Project, vararg actions: AnAction) =
            showNotification(message, project, NotificationType.ERROR, *actions)

        fun showInfo(message: String, project: Project, vararg actions: AnAction) =
            showNotification(message, project, NotificationType.INFORMATION, *actions)

        fun showWarn(message: String, project: Project, vararg actions: AnAction) =
            showNotification(message, project, NotificationType.WARNING, *actions)

        // TODO(pavel): refactor showNotification function to make it more generic + default arguments
        private fun showNotification(
            message: String,
            project: Project,
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

        fun showWelcomeNotification(project: Project) {
            val welcomeMessage = "Welcome to Snyk! Check out our tool window to start analyzing your code"
            logger.info(welcomeMessage)
            val notification = GROUP.createNotification(
                welcomeMessage,
                NotificationType.INFORMATION
            ).addAction(
                NotificationAction.createSimpleExpiring("Configure Snyk\u2026") {
                    snykToolWindow(project)?.show()
                }
            )
            notification.notify(project)
        }

        fun showSastForOrgEnablement(project: Project): Notification {
            val notification = showInfo(
                "$sastForOrgEnablementMessage To enable navigate to ",
                project,
                NotificationAction.createSimpleExpiring("Snyk > Settings > Snyk Code") {
                    BrowserUtil.browse(getSnykCodeSettingsUrl())
                    startSastEnablementCheckLoop(project)
                }
            )
            var currentAttempt = 1
            val maxAttempts = 200
            lateinit var checkIfSastEnabled: () -> Unit
            checkIfSastEnabled = {
                if (pluginSettings().sastOnServerEnabled == true) {
                    notification.expire()
                } else if (!alarm.isDisposed && currentAttempt < maxAttempts) {
                    currentAttempt++
                    alarm.addRequest(checkIfSastEnabled, 1000)
                }
            }
            checkIfSastEnabled.invoke()

            return notification
        }

        fun showFeedbackRequest(project: Project) = showInfo(
            "Thank you for using Snyk! Want to help us by taking part in Snyk’s plugin research and get a \$100 Amazon gift card in return?",
            project,
            NotificationAction.createSimpleExpiring("Schedule user testing here") {
                pluginSettings().showFeedbackRequest = false
                BrowserUtil.browse("https://calendly.com/snyk-georgi/45min")
            },
            NotificationAction.createSimpleExpiring("Don’t show again") {
                pluginSettings().showFeedbackRequest = false
            }
        )

        fun showScanningReminder(project: Project) = showInfo(
            "Scan your project for security vulnerabilities and code issues",
            project,
            NotificationAction.createSimpleExpiring("Run scan") {
                snykToolWindow(project)?.show()
                project.service<SnykTaskQueueService>().scan()
                project.service<SnykAnalyticsService>().logAnalysisIsTriggered(
                    AnalysisIsTriggered.builder()
                        .analysisType(getSelectedProducts(pluginSettings()))
                        .ide(AnalysisIsTriggered.Ide.JETBRAINS)
                        .triggeredByUser(true)
                        .build()
                )
            }
        )

        fun showNetworkErrorAlert(project: Project) = showError(
            "$networkErrorAlertMessage Check connection and network settings.",
            project,
            NotificationAction.createSimpleExpiring("Snyk Settings") {
                ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, SnykProjectSettingsConfigurable::class.java)
            }
        )

        fun showInfoBalloonForComponent(message: String, component: Component, showAbove: Boolean = false): Balloon {
            return createBalloon(message, AllIcons.General.BalloonInformation, MessageType.INFO.popupBackground).apply {
                showBalloonForComponent(this, component, showAbove)
            }
        }

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

            showBalloonForComponent(balloon, mouseEvent.component, true, mouseEvent.point)

            return balloon
        }

        fun showWarnBalloonAtEventPlace(message: String, e: AnActionEvent, showAbove: Boolean = false) {
            val component = e.inputEvent?.component ?: e.getData(CONTEXT_COMPONENT) ?: return
            //todo: case if no Component exist (action invoked from Search?)
            val balloon = createBalloon(message, AllIcons.General.BalloonWarning, MessageType.WARNING.popupBackground)
            showBalloonForComponent(balloon, component, showAbove)
        }

        private fun createBalloon(message: String, icon: Icon, color: Color): Balloon =
            JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, icon, color, null)
                .setHideOnClickOutside(true)
                .setFadeoutTime(5000)
                .createBalloon()

        private fun showBalloonForComponent(balloon: Balloon, component: Component, showAbove: Boolean, point: Point? = null) {
            balloon.show(
                RelativePoint(
                    component,
                    point ?: Point(component.width / 2, if (showAbove) 0 else component.height)
                ),
                if (showAbove) Balloon.Position.above else Balloon.Position.below
            )
        }
    }
}
