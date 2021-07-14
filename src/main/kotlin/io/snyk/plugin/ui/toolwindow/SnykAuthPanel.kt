package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.segment.analytics.Analytics
import com.segment.analytics.messages.IdentifyMessage
import icons.SnykIcons
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykCliAuthenticationService
import io.snyk.plugin.services.SnykCliDownloaderService
import io.snyk.plugin.snykcode.core.SnykCodeParams
import io.snyk.plugin.ui.boldLabel
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

class SnykAuthPanel(project: Project) : JPanel(), Disposable {

    private fun baseGridConstraints(
        row: Int,
        column: Int = 0,
        rowSpan: Int = 1,
        colSpan: Int = 1,
        anchor: Int = GridConstraints.ANCHOR_CENTER,
        fill: Int = GridConstraints.FILL_NONE,
        HSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
        VSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
        minimumSize: Dimension? = null,
        preferredSize: Dimension? = null,
        maximumSize: Dimension? = null,
        indent: Int = 1,
        useParentLayout: Boolean = false
    ): GridConstraints {
        return GridConstraints(
            row, column, rowSpan, colSpan, anchor, fill, HSizePolicy, VSizePolicy, minimumSize, preferredSize,
            maximumSize, indent, useParentLayout
        )
    }

    val authButton: JButton

    init {
        layout = GridLayoutManager(4, 1, Insets(0, 0, 0, 0), -1, -1)

        add(JLabel(SnykIcons.LOGO), baseGridConstraints(0))

        add(boldLabel("Welcome to Snyk for JetBrains"), baseGridConstraints(1))

        add(JLabel("Please authenticate to Snyk and connect your IDE"), baseGridConstraints(2))

        authButton = JButton(object : AbstractAction("Connect your IDE to Snyk") {
            override fun actionPerformed(e: ActionEvent?) {
                project.service<SnykToolWindowPanel>().cleanUiAndCaches()

                val token = project.service<SnykCliAuthenticationService>().authenticate()
                getApplicationSettingsStateService().token = token
                SnykCodeParams.instance.sessionToken = token

                val analytics = service<SnykAnalyticsService>()
                val userId = analytics.obtainUserId(token)
                if (userId.isNotBlank()) {
                    analytics.setUserId(userId)
                    analytics.identify()
                }

                getSyncPublisher(project, SnykSettingsListener.SNYK_SETTINGS_TOPIC)?.settingsChanged()
            }
        }).apply {
            isEnabled = !service<SnykCliDownloaderService>().isCliDownloading()
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

        add(authButton, baseGridConstraints(3))
    }

    override fun dispose() {}
}
