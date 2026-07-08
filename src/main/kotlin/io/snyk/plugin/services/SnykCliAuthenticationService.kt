package io.snyk.plugin.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PlatformIcons
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsync
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.util.concurrent.CancellationException
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.ScrollPaneConstants
import snyk.common.lsp.LanguageServerWrapper

@Service(Service.Level.PROJECT)
class SnykCliAuthenticationService(val project: Project) {
  private val logger = logger<SnykCliAuthenticationService>()

  /**
   * Log out of Snyk: clears the stored token both in the Language Server and locally, then returns
   * the tool window to the authentication panel. Runs on a pooled thread so callers (actions, UI)
   * do not block. Clearing the local token is what stops it being re-sent to the LS on the next
   * workspace/didChangeConfiguration push.
   */
  fun logout() {
    ApplicationManager.getApplication().executeOnPooledThread {
      if (project.isDisposed) return@executeOnPooledThread
      // Clear the local token before logging the LS out. If the LS pulls workspace/configuration
      // while handling logout, getSettings() would otherwise hand the stale token straight back
      // and re-authenticate the server we just logged out.
      pluginSettings().token = ""
      LanguageServerWrapper.getInstance(project).logout()

      ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        getSnykToolWindowPanel(project)?.cleanUiAndCaches()
      }
      // Re-render the tool window; with an empty token it falls back to the authentication panel.
      publishAsync(project, SnykSettingsListener.SNYK_SETTINGS_TOPIC) { settingsChanged() }
    }
  }

  fun authenticate() {
    fun showAuthDialog() {
      if (!getCliFile().exists()) return

      val dialog = AuthDialog(project)
      val languageServerWrapper = LanguageServerWrapper.getInstance(project)

      // Set up cancel handler
      dialog.onCancel = { languageServerWrapper.cancelPreviousLogin() }

      // Start login in background, then show dialog
      ApplicationManager.getApplication().executeOnPooledThread {
        languageServerWrapper.logout()
        val loginFuture = languageServerWrapper.login()

        // Enable copy URL button on EDT
        ApplicationManager.getApplication()
          .invokeLater({ dialog.copyUrlAction.isEnabled = true }, ModalityState.any())

        loginFuture?.whenComplete { result, e ->
          val cancelled = e is CancellationException
          if (cancelled) {
            logger.debug("login timed out or cancelled", e)
          } else if (e != null) {
            logger.warn("could not login", e)
          }

          // Only set token if result is not blank (avoid overwriting token from hasAuthenticated)
          if (!cancelled) {
            val token = result?.toString() ?: ""
            if (token.isNotBlank()) {
              pluginSettings().token = token
            } else {
              logger.warn("no token returned by login")
            }
          }
          val exitCode =
            if (!pluginSettings().token.isNullOrBlank()) {
              DialogWrapper.OK_EXIT_CODE
            } else {
              DialogWrapper.CLOSE_EXIT_CODE
            }

          ApplicationManager.getApplication()
            .invokeLater({ dialog.close(exitCode) }, ModalityState.any())

          languageServerWrapper.updateConfiguration(false)
        }
      }

      dialog.show()
    }

    ApplicationManager.getApplication()
      .invokeLater(
        {
          if (project.isDisposed) return@invokeLater
          if (getCliFile().exists()) {
            showAuthDialog()
            return@invokeLater
          }

          ProgressManager.getInstance()
            .run(
              object : Task.Modal(project, "Download latest Snyk CLI", true) {
                override fun run(indicator: ProgressIndicator) {
                  getSnykTaskQueueService(project)?.waitUntilCliDownloadedIfNeeded()
                }

                override fun onSuccess() {
                  if (project.isDisposed) return
                  showAuthDialog()
                }
              }
            )
        },
        ModalityState.any(),
      )
  }
}

class AuthDialog(val project: Project) : DialogWrapper(project) {
  var onCancel: () -> Unit = {}
  private val viewer =
    getReadOnlyClickableHtmlJEditorPane(
      """
        <html>
            We are now redirecting you to our auth page, go ahead and log in.<br><br>
            Once the authentication is complete, return to the IDE and you'll be ready to start using Snyk.<br><br>
            If a browser window doesn't open after a few seconds, please copy the url using the button below and manually paste it in a browser.
        </html>
        """
        .trimIndent()
    )
  val copyUrlAction = CopyUrlAction()

  init {
    super.init()
    copyUrlAction.isEnabled = false
    title = "Authenticating Snyk Plugin"
  }

  override fun createCenterPanel(): JComponent {
    val centerPanel = JPanel(BorderLayout(JBUIScale.scale(5), JBUIScale.scale(5)))
    val scrollPane =
      JBScrollPane(
        viewer,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
      )
    centerPanel.add(scrollPane, BorderLayout.CENTER)

    val progressBar = JProgressBar().apply { isIndeterminate = true }
    centerPanel.add(progressBar, BorderLayout.SOUTH)

    centerPanel.preferredSize = Dimension(500, 150)

    return centerPanel
  }

  override fun createActions(): Array<Action> = arrayOf(cancelAction)

  override fun doCancelAction() {
    super.doCancelAction()
    onCancel()
  }

  override fun createLeftSideActions(): Array<Action> = arrayOf(copyUrlAction)

  inner class CopyUrlAction : AbstractAction("&Copy URL", PlatformIcons.COPY_ICON) {
    override fun actionPerformed(e: ActionEvent) {
      val url = LanguageServerWrapper.getInstance(project).getAuthLink()
      if (url != null) {
        CopyPasteManager.getInstance().setContents(StringSelection(url))
        SnykBalloonNotificationHelper.showInfoBalloonForComponent(
          "URL copied",
          getButton(this) ?: viewer,
          showAbove = getButton(this) != null,
        )
      }
    }
  }
}
