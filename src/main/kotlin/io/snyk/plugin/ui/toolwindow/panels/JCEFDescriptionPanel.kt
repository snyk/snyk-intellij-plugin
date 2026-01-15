package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.jcef.ApplyAiFixEditHandler
import io.snyk.plugin.ui.jcef.GenerateAIFixHandler
import io.snyk.plugin.ui.jcef.IgnoreInFileHandler
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.LoadHandlerGenerator
import io.snyk.plugin.ui.jcef.OpenFileLoadHandlerGenerator
import io.snyk.plugin.ui.jcef.SubmitIgnoreRequestHandler
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import io.snyk.plugin.ui.wrapWithScrollPane
import snyk.common.lsp.ScanIssue
import java.awt.BorderLayout
import javax.swing.JPanel

class SuggestionDescriptionPanel(
    val project: Project,
    private val issue: ScanIssue,
) : JPanel(BorderLayout()), IssueDescriptionPanel {
    private val unexpectedErrorMessage =
        "Snyk encountered an issue while rendering the vulnerability description. Please try again, or contact support if the problem persists. We apologize for any inconvenience caused."

    init {
        // Show loading state immediately to avoid blocking EDT
        val loadingPanel = StatePanel("Loading issue details...")
        this.add(wrapWithScrollPane(loadingPanel), BorderLayout.CENTER)

        // Load content asynchronously to avoid EDT blocking
        // issue.details() may call generateIssueDescription() which makes a blocking LS call
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread

            // Fetch issue details in background (may block on LS call)
            val issueDetails = issue.details(project)

            // Switch back to EDT for UI updates and theme-dependent operations
            invokeLater {
                if (project.isDisposed) return@invokeLater
                initializeBrowser(issueDetails)
            }
        }
    }

    private fun initializeBrowser(issueDetails: String) {
        val loadHandlerGenerators: MutableList<LoadHandlerGenerator> =
            emptyList<LoadHandlerGenerator>().toMutableList()

        when (issue.filterableIssueType) {
            ScanIssue.CODE_SECURITY -> {
                val virtualFiles = LinkedHashMap<String, VirtualFile?>()
                for (dataFlow in issue.additionalData.dataFlow) {
                    virtualFiles[dataFlow.filePath] = dataFlow.filePath.toVirtualFile()
                }

                val openFileLoadHandlerGenerator = OpenFileLoadHandlerGenerator(project, virtualFiles)
                loadHandlerGenerators += {
                    openFileLoadHandlerGenerator.generate(it)
                }

                val generateAIFixHandler = GenerateAIFixHandler(project)
                loadHandlerGenerators += {
                    generateAIFixHandler.generateAIFixCommand(it)
                }

                val applyAiFixEditHandler = ApplyAiFixEditHandler(project)
                loadHandlerGenerators += {
                    applyAiFixEditHandler.generateApplyAiFixEditCommand(it)
                }

                val submitIgnoreRequestHandler = SubmitIgnoreRequestHandler(project)
                loadHandlerGenerators += {
                    submitIgnoreRequestHandler.submitIgnoreRequestCommand(it)
                }
            }

            ScanIssue.INFRASTRUCTURE_AS_CODE -> {
                val applyIgnoreInFileHandler = IgnoreInFileHandler(project)
                loadHandlerGenerators += {
                    applyIgnoreInFileHandler.generateIgnoreInFileCommand(it)
                }
            }
        }

        // getFormattedHtml accesses Swing components, must be called on EDT
        val html = PanelHTMLUtils.getFormattedHtml(issueDetails)
        val jbCefBrowser =
            JCEFUtils.getJBCefBrowserIfSupported(html, loadHandlerGenerators)

        // Remove loading panel and show actual content
        this.removeAll()

        if (jbCefBrowser == null) {
            val statePanel = StatePanel(SnykToolWindowPanel.SELECT_ISSUE_TEXT)
            this.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
            SnykBalloonNotificationHelper.showError(unexpectedErrorMessage, null)
        } else {
            val lastRowToAddSpacer = 5
            val panel =
                JPanel(
                    GridLayoutManager(lastRowToAddSpacer + 1, 1, JBUI.insets(0, 10, 20, 10), -1, 20),
                ).apply {
                    this.add(
                        jbCefBrowser.component,
                        panelGridConstraints(1),
                    )
                }
            this.add(
                wrapWithScrollPane(panel),
                BorderLayout.CENTER,
            )
            this.add(panel)
        }

        this.revalidate()
        this.repaint()
    }
}

