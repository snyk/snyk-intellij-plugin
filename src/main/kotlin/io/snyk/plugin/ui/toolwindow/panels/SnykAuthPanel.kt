package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_EAST
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTHWEST
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_SOUTHWEST
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.publishAsync
import io.snyk.plugin.ui.addAndGetCenteredPanel
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.boldLabel
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPaneFixedSize
import io.snyk.plugin.ui.getStandardLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import snyk.SnykBundle
import snyk.trust.WorkspaceTrustService

class SnykAuthPanel(val project: Project) : JPanel(), Disposable {

  init {
    name = "authPanel"
    val authButton =
      JButton(
          object : AbstractAction(TRUST_AND_SCAN_BUTTON_TEXT) {
            override fun actionPerformed(e: ActionEvent?) {
              isEnabled = false
              val waitingMessage = "Waiting for indexing to finish..."
              val jButton = e?.source as JButton
              jButton.text = waitingMessage
              DumbService.getInstance(project).runWhenSmart {
                getSnykToolWindowPanel(project)?.cleanUiAndCaches()

                jButton.setText("Authenticating...")
                getSnykCliAuthenticationService(project)?.authenticate() ?: ""

                // explicitly add the project to workspace trusted paths, because
                // scan can be auto-triggered depending on "settings.pluginFirstRun" value
                jButton.setText("Trusting project paths...")
                val trustService = service<WorkspaceTrustService>()
                val paths = project.getContentRootPaths()
                for (path in paths) {
                  trustService.addTrustedPath(path)
                }
                publishAsync(project, SnykSettingsListener.SNYK_SETTINGS_TOPIC) {
                  settingsChanged()
                }
              }
            }
          }
        )
        .apply { isEnabled = !getSnykCliDownloaderService().isCliDownloading() }

    layout = getStandardLayout(1, 1)
    val panel = addAndGetCenteredPanel(this, 4, 2)
    panel.add(
      boldLabel("Welcome to Snyk for JetBrains!"),
      baseGridConstraints(row = 0, column = 1, anchor = ANCHOR_SOUTHWEST),
    )
    panel.add(
      JLabel(SnykIcons.LOGO),
      baseGridConstraints(row = 1, column = 0, anchor = ANCHOR_EAST),
    )
    panel.add(
      JLabel(descriptionLabelText()),
      baseGridConstraints(row = 1, column = 1, anchor = ANCHOR_WEST),
    )
    panel.add(authButton, baseGridConstraints(row = 2, column = 1, anchor = ANCHOR_NORTHWEST))

    panel.add(
      getReadOnlyClickableHtmlJEditorPaneFixedSize(
        messagePolicyAndTermsHtml,
        font = io.snyk.plugin.ui.getFont(-1, 11, UIUtil.getLabelFont()) ?: UIUtil.getLabelFont(),
      ),
      baseGridConstraints(row = 3, column = 1, anchor = ANCHOR_SOUTHWEST),
    )

    ApplicationManager.getApplication()
      .messageBus
      .connect(this@SnykAuthPanel)
      .subscribe(
        SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
        object : SnykCliDownloadListener {
          override fun cliDownloadStarted() {
            authButton.isEnabled = false
          }

          override fun cliDownloadFinished(succeed: Boolean) {
            authButton.isEnabled = succeed
          }
        },
      )
  }

  private fun descriptionLabelText(): String {
    val trustWarningDescription = SnykBundle.message("snyk.panel.auth.trust.warning.text")
    return """
        |<html>
        |<ol>
        |  <li align="left">Authenticate to Snyk.io</li>
        |  <li align="left">Analyze code for issues and vulnerabilities</li>
        |  <li align="left">Improve your code and upgrade dependencies</li>
        |</ol>
        |<br>
        |$trustWarningDescription
        |<br>
        |<br>
        |</html>
        """
      .trimMargin()
  }

  override fun dispose() {}

  companion object {
    const val TRUST_AND_SCAN_BUTTON_TEXT = "Trust project and scan"
    val messagePolicyAndTermsHtml =
      """
                <br>
                By connecting your account with Snyk, you agree to<br>
                the Snyk <a href="https://snyk.io/policies/privacy/">Privacy Policy</a>,
                and the Snyk <a href="https://snyk.io/policies/terms-of-service/">Terms of Service</a>.
            """
        .trimIndent()
  }
}
