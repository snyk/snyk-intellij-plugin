package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_EAST
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTHWEST
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_SOUTHWEST
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST
import com.intellij.uiDesigner.core.GridLayoutManager
import icons.SnykIcons
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getAmplitudeExperimentService
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.snykcode.core.SnykCodeParams
import io.snyk.plugin.ui.addAndGetCenteredPanel
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.boldLabel
import io.snyk.plugin.ui.getStandardLayout
import snyk.amplitude.api.ExperimentUser
import snyk.analytics.AuthenticateButtonIsClicked
import snyk.analytics.AuthenticateButtonIsClicked.EventSource
import snyk.analytics.AuthenticateButtonIsClicked.Ide
import snyk.analytics.AuthenticateButtonIsClicked.builder
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class SnykAuthPanel(val project: Project) : JPanel(), Disposable {

    init {
        name = "authPanel"
        val authButton = JButton(object : AbstractAction(authenticateButtonText()) {
            override fun actionPerformed(e: ActionEvent?) {
                val analytics = service<SnykAnalyticsService>()
                analytics.logAuthenticateButtonIsClicked(authenticateEvent())
                getSnykToolWindowPanel(project)?.cleanUiAndCaches()

                val token = getSnykCliAuthenticationService(project)?.authenticate() ?: ""
                pluginSettings().token = token
                SnykCodeParams.instance.sessionToken = token

                val userId = analytics.obtainUserId(token)
                if (userId.isNotBlank()) {
                    analytics.setUserId(userId)
                    analytics.identify()
                    getAmplitudeExperimentService(project)?.fetch(ExperimentUser(userId))
                }
                getSyncPublisher(project, SnykSettingsListener.SNYK_SETTINGS_TOPIC)?.settingsChanged()
            }
        }).apply {
            isEnabled = getSnykCliDownloaderService(project)?.isCliDownloading() == false
        }
        if (getAmplitudeExperimentService(project)?.isPartOfExperimentalWelcomeWorkflow() == false) {
            layout = GridLayoutManager(4, 1, Insets(0, 0, 0, 0), -1, -1)
            add(JLabel(SnykIcons.LOGO), baseGridConstraints(0))
            add(boldLabel("Welcome to Snyk for JetBrains!"), baseGridConstraints(1))
            add(JLabel(descriptionLabelText()), baseGridConstraints(2))
            add(authButton, baseGridConstraints(3))
        } else {
            layout = getStandardLayout(1, 1)
            val panel = addAndGetCenteredPanel(this, 3, 2)
            panel.add(
                boldLabel("Welcome to Snyk for JetBrains!"),
                baseGridConstraints(row = 0, column = 1, anchor = ANCHOR_SOUTHWEST)
            )
            panel.add(
                JLabel(SnykIcons.LOGO),
                baseGridConstraints(row = 1, column = 0, anchor = ANCHOR_EAST)
            )
            panel.add(
                JLabel(descriptionLabelText()),
                baseGridConstraints(row = 1, column = 1, anchor = ANCHOR_WEST)
            )
            panel.add(authButton, baseGridConstraints(row = 2, column = 1, anchor = ANCHOR_NORTHWEST))
        }

        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object : SnykCliDownloadListener {
                override fun cliDownloadStarted() {
                    authButton.isEnabled = false
                }

                override fun cliDownloadFinished(succeed: Boolean) {
                    authButton.isEnabled = true
                }
            })
    }

    private fun authenticateEvent(): AuthenticateButtonIsClicked {
        return builder().ide(Ide.JETBRAINS).eventSource(EventSource.IDE).build()
    }

    private fun descriptionLabelText(): String {
        if (getAmplitudeExperimentService(project)?.isPartOfExperimentalWelcomeWorkflow() == true) {
            return """
        |<html><ol>
        |  <li align="left">Authenticate to Snyk.io</li>
        |  <li align="left">Analyze code for issues and vulnerabilities</li>
        |  <li align="left">Improve you code and upgrade dependencies</li>
        |</ol>
        |</html>
        """.trimMargin()
        }
        return "Please authenticate to Snyk and connect your IDE"
    }

    fun authenticateButtonText(): String {
        if (getAmplitudeExperimentService(project)?.isPartOfExperimentalWelcomeWorkflow() == true) {
            return "Test code now"
        }
        return "Connect your IDE to Snyk"
    }

    override fun dispose() {}
}
