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
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import org.apache.commons.text.StringEscapeUtils.escapeHtml4
import snyk.common.lsp.LanguageServerWrapper
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.ScrollPaneConstants

@Service(Service.Level.PROJECT)
class SnykCliAuthenticationService(val project: Project) {
    private val logger = logger<SnykCliAuthenticationService>()

    private var isAuthenticated = false
    private var token: String = ""

    fun authenticate(): String {
        token = ""
        downloadCliIfNeeded()
        if (getCliFile().exists()) executeAuthCommand()
        if (isAuthenticated) executeGetConfigApiCommand()
        LanguageServerWrapper.getInstance().updateConfiguration()
        return token
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
            downloadCliTask, "Download Snyk CLI latest release", true, null
        )
    }

    private fun executeAuthCommand() {
        val dialog = AuthDialog()

        object : Task.Backgroundable(project, "Authenticating Snyk plugin...", true) {
            override fun run(indicator: ProgressIndicator) {
                dialog.onCancel = { indicator.cancel() }
                val cmd = mutableListOf("auth")
                if (pluginSettings().useTokenAuthentication) {
                    cmd.add("--auth-type=token")
                } else {
                    cmd.add("--auth-type=oauth")
                }
                val commands = buildCliCommands(cmd)
                val finalOutput = getConsoleCommandRunner().execute(commands, getPluginPath(), "", project) { line ->
                    if (line.startsWith("https://")) {
                        val htmlLink = escapeHtml4(line.removeLineEnd())
                        val htmlText =
                            """<html>
                                We are now redirecting you to our auth page, go ahead and log in.<br><br>
                                Once the authentication is complete, return to the IDE and you'll be ready to start using Snyk.<br><br>
                                If a browser window doesn't open after a few seconds, please <a href="$htmlLink">click here</a>
                                or copy the url using the button below and manually paste it in a browser.
                            </html>
                            """.trimIndent()
                        dialog.updateHtmlText(htmlText)
                        dialog.copyUrlAction.url = htmlLink
                        dialog.copyUrlAction.isEnabled = true
                    }
                }
                val authSucceed = finalOutput.contains("Your account has been authenticated.")
                if (!authSucceed && finalOutput != ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER) {
                    SnykBalloonNotificationHelper.showError("Failed to authenticate.", project)
                }
                val exitCode = if (authSucceed) DialogWrapper.OK_EXIT_CODE else DialogWrapper.CLOSE_EXIT_CODE
                ApplicationManager.getApplication().invokeLater(
                    { dialog.close(exitCode) },
                    ModalityState.any()
                )
            }
        }.queue()

        isAuthenticated = dialog.showAndGet()
    }

    fun executeGetConfigApiCommand() {
        val getConfigApiTask: () -> Unit = {
            val key = "INTERNAL_OAUTH_TOKEN_STORAGE"
            val commands = buildCliCommands(listOf("config", "get", key))
            val getConfigApiOutput = getConsoleCommandRunner().execute(commands, getPluginPath(), "", project)
            token = getConfigApiOutput.removeLineEnd()
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            getConfigApiTask, "Get Snyk API Token", true, null
        )
    }

    private fun buildCliCommands(commands: List<String>): List<String> {
        val settings = pluginSettings()
        val cli: MutableList<String> = mutableListOf(getCliFile().absolutePath)
        cli.addAll(commands)

        if (settings.ignoreUnknownCA) {
            cli.add("--insecure")
        }

        return cli.toList()
    }

    private fun getConsoleCommandRunner(): ConsoleCommandRunner {
        return ConsoleCommandRunner()
    }
}

class AuthDialog : DialogWrapper(true) {
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
        val scrollPane = JBScrollPane(
            viewer,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        centerPanel.add(scrollPane, BorderLayout.CENTER)

        val progressBar = JProgressBar().apply {
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

    inner class CopyUrlAction(var url: String = "") : AbstractAction("&Copy URL", PlatformIcons.COPY_ICON) {
        override fun actionPerformed(e: ActionEvent) {
            CopyPasteManager.getInstance().setContents(StringSelection(url))
            SnykBalloonNotificationHelper.showInfoBalloonForComponent(
                "URL copied",
                getButton(this) ?: viewer,
                showAbove = getButton(this) != null
            )
        }
    }
}

fun String.removeLineEnd(): String = this.replace("\n", "").replace("\r", "")
