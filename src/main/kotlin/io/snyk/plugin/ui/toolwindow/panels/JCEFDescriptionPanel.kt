package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.descriptionHeaderPanel
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
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.collections.set

class SuggestionDescriptionPanelFromLS(
    val project: Project,
    private val issue: ScanIssue,
) : IssueDescriptionPanelBase(
    title = issue.title(),
    severity = issue.getSeverityAsEnum(),
) {
    private val unexpectedErrorMessage =
        "Snyk encountered an issue while rendering the vulnerability description. Please try again, or contact support if the problem persists. We apologize for any inconvenience caused."

    init {
        val loadHandlerGenerators: MutableList<LoadHandlerGenerator> =
            emptyList<LoadHandlerGenerator>().toMutableList()

        // TODO: replace directly in HTML instead of JS

        when (issue.filterableIssueType) {
            ScanIssue.CODE_QUALITY, ScanIssue.CODE_SECURITY -> {
                val virtualFiles = LinkedHashMap<String, VirtualFile?>()
                for (dataFlow in issue.additionalData.dataFlow) {
                    virtualFiles[dataFlow.filePath] = dataFlow.filePath.toVirtualFile()
                }

                val openFileLoadHandlerGenerator = OpenFileLoadHandlerGenerator(project, virtualFiles)
                loadHandlerGenerators += {
                    openFileLoadHandlerGenerator.generate(it)
                }

                val generateAIFixHandler = GenerateAIFixHandler()
                loadHandlerGenerators += {
                    generateAIFixHandler.generateAIFixCommand(it)
                }

                val applyAiFixEditHandler = ApplyAiFixEditHandler(project)
                loadHandlerGenerators += {
                    applyAiFixEditHandler.generateApplyAiFixEditCommand(it)
                }

                val submitIgnoreRequestHandler = SubmitIgnoreRequestHandler()
                loadHandlerGenerators += {
                    submitIgnoreRequestHandler.submitIgnoreRequestCommand(it)
                }

            }
            ScanIssue.INFRASTRUCTURE_AS_CODE ->
            {
                val applyIgnoreInFileHandler = IgnoreInFileHandler(project)
                loadHandlerGenerators +={
                    applyIgnoreInFileHandler.generateIgnoreInFileCommand(it)
                }
            }
        }
        val html = this.getCustomCssAndScript()
        val jbCefBrowser =
            JCEFUtils.getJBCefBrowserIfSupported(html, loadHandlerGenerators)
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
    }

    override fun secondRowTitlePanel(): DescriptionHeaderPanel =
        descriptionHeaderPanel(
            issueNaming = issue.issueNaming(),
            cwes = issue.cwes(),
            cvssScore = issue.cvssScore(),
            cvssV3 = issue.cvssV3(),
            cves = issue.cves(),
            id = issue.id(),
        )

    override fun createMainBodyPanel(): Pair<JPanel, Int> {
        val lastRowToAddSpacer = 5
        val panel =
            JPanel(
                GridLayoutManager(lastRowToAddSpacer + 1, 1, JBUI.insets(0, 10, 20, 10), -1, 20),
            ).apply {
                when (issue.filterableIssueType) {
                    ScanIssue.CODE_SECURITY, ScanIssue.CODE_QUALITY -> {
                        this.add(
                            SnykCodeOverviewPanel(issue.additionalData),
                            panelGridConstraints(2),
                        )
                        this.add(
                            SnykCodeDataflowPanel(project, issue.additionalData),
                            panelGridConstraints(3),
                        )
                        this.add(
                            SnykCodeExampleFixesPanel(issue.additionalData),
                            panelGridConstraints(4),
                        )
                    }

                    ScanIssue.OPEN_SOURCE -> {
                        this.add(
                            SnykOSSIntroducedThroughPanel(issue.additionalData),
                            baseGridConstraintsAnchorWest(1, indent = 0),
                        )
                        this.add(
                            SnykOSSDetailedPathsPanel(issue.additionalData),
                            panelGridConstraints(2),
                        )
                        this.add(
                            SnykOSSOverviewPanel(issue.additionalData),
                            panelGridConstraints(3),
                        )
                    }

                    else -> {
                        // do nothing
                    }
                }
            }
        return Pair(panel, lastRowToAddSpacer)
    }

    fun getCustomCssAndScript(): String {
        val html = issue.details()
        return PanelHTMLUtils.getFormattedHtml(html)
    }
}

fun defaultFontLabel(
    labelText: String,
    bold: Boolean = false,
): JLabel {
    return JLabel().apply {
        val titleLabelFont: Font? = io.snyk.plugin.ui.getFont(if (bold) Font.BOLD else -1, 14, font)
        titleLabelFont?.let { font = it }
        text = labelText
    }
}
