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
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import snyk.common.lsp.LanguageServerWrapper
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

@Service(Service.Level.PROJECT)
class SnykCliAuthenticationService(
    val project: Project,
) {
    private val logger = logger<SnykCliAuthenticationService>()

    fun authenticate() {
        downloadCliIfNeeded()
        if (getCliFile().exists()) {
            executeAuthCommand()
        }
        LanguageServerWrapper.getInstance(project).updateConfiguration(false)
    }

    private fun downloadCliIfNeeded() {
        val downloadCliTask: () -> Unit = {
            if (!getCliFile().exists()) {
                getSnykTaskQueueService(project)?.downloadLatestRelease()
            } else {
                logger.debug("Skip CLI download, since it was already downloaded")
            }
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            downloadCliTask,
            "Download Snyk CLI latest release",
            true,
            null,
        )
    }

    private fun executeAuthCommand() {
        val dialog = AuthDialog(project)

        object : Task.Backgroundable(project, "Authenticating Snyk plugin...", true) {
            override fun run(indicator: ProgressIndicator) {
                val languageServerWrapper = LanguageServerWrapper.getInstance(project)
                dialog.onCancel = {
                    languageServerWrapper.cancelPreviousLogin()
                    indicator.cancel()
                }
                languageServerWrapper.logout()
                val htmlText =
                    """
                    <html>
                        We are now redirecting you to our auth page, go ahead and log in.<br><br>
                        Once the authentication is complete, return to the IDE and you'll be ready to start using Snyk.<br><br>
                        If a browser window doesn't open after a few seconds, please copy the url using the button below and manually paste it in a browser.
                    </html>
                    """.trimIndent()
                dialog.updateHtmlText(htmlText)
                dialog.copyUrlAction.isEnabled = true
                languageServerWrapper.login()
                    ?.whenComplete { result, e ->
                        var token: String
                        if (e != null) {
                            if (e is CancellationException) {
                                logger.warn("login timed out or cancelled", e)
                            } else {
                                logger.warn("could not login", e)
                            }
                            token = ""
                        } else {
                            token = result?.toString() ?: ""
                        }
                        pluginSettings().token = token
                        val exitCode = if (!pluginSettings().token.isNullOrBlank()) {
                            DialogWrapper.OK_EXIT_CODE
                        } else {
                            DialogWrapper.CLOSE_EXIT_CODE
                        }
                        ApplicationManager.getApplication().invokeLater({ dialog.close(exitCode) }, ModalityState.any())
                    }
            }
        }.queue()

        dialog.showAndGet()
    }
}

class AuthDialog(val project: Project) : DialogWrapper(true) {
    var onCancel: () -> Unit = {}
    private val viewer = getReadOnlyClickableHtmlJEditorPane("Initializing Authentication...")
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

        val progressBar =
            JProgressBar().apply {
                isIndeterminate = true
            }
        centerPanel.add(progressBar, BorderLayout.SOUTH)

        centerPanel.preferredSize = Dimension(500, 150)

        return centerPanel
    }

    fun updateHtmlText(htmlText: String) {
        viewer.text = htmlText
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
