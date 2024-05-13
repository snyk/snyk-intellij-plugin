package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.SnykFile
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.OpenFileLoadHandlerGenerator
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import io.snyk.plugin.ui.wrapWithScrollPane
import snyk.common.lsp.ScanIssue
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class SuggestionDescriptionPanelFromLS(
    snykFile: SnykFile,
    private val issue: ScanIssue
) : IssueDescriptionPanelBase(
    title = getIssueTitle(issue),
    severity = issue.getSeverityAsEnum()
) {
    val project = snykFile.project
    private val unexpectedErrorMessage =
        "Snyk encountered an issue while rendering the vulnerability description. Please try again, or contact support if the problem persists. We apologize for any inconvenience caused."

    init {
        if (pluginSettings().isGlobalIgnoresFeatureEnabled && issue.additionalData.details != null) {
            val openFileLoadHandlerGenerator = OpenFileLoadHandlerGenerator(snykFile)
            val jbCefBrowserComponent = JCEFUtils.getJBCefBrowserComponentIfSupported(issue.additionalData.details) {
                openFileLoadHandlerGenerator.generate(it)
            }
            if (jbCefBrowserComponent == null) {
                val statePanel = StatePanel(SnykToolWindowPanel.SELECT_ISSUE_TEXT)
                this.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
                SnykBalloonNotificationHelper.showError(unexpectedErrorMessage, null)
            } else {
                val lastRowToAddSpacer = 5
                val panel = JPanel(
                    GridLayoutManager(lastRowToAddSpacer + 1, 1, JBUI.insets(0, 10, 20, 10), -1, 20)
                ).apply {
                    this.add(
                        jbCefBrowserComponent,
                        panelGridConstraints(1)
                    )
                }
                this.add(
                    wrapWithScrollPane(panel),
                    BorderLayout.CENTER
                )
                this.add(panel)
            }
        } else {
            createUI()
        }
    }

    override fun secondRowTitlePanel(): DescriptionHeaderPanel = descriptionHeaderPanel(
        issueNaming = if (issue.additionalData.isSecurityType) "Vulnerability" else "Quality Issue",
        cwes = issue.additionalData.cwe ?: emptyList()
    )

    override fun createMainBodyPanel(): Pair<JPanel, Int> {
        val lastRowToAddSpacer = 5
        val panel = JPanel(
            GridLayoutManager(lastRowToAddSpacer + 1, 1, JBUI.insets(0, 10, 20, 10), -1, 20)
        ).apply {
            overviewPanel()?.let { this.add(it, panelGridConstraints(2)) }
            dataFlowPanel()?.let { this.add(it, panelGridConstraints(3)) }
            fixExamplesPanel()?.let { this.add(it, panelGridConstraints(4)) }
        }
        return Pair(panel, lastRowToAddSpacer)
    }

    private fun overviewPanel(): JComponent? {
        return SnykCodeOverviewPanel(issue.additionalData)
    }

    private fun dataFlowPanel(): JPanel? {
        return SnykCodeDataflowPanel(project, issue.additionalData)
    }

    private fun fixExamplesPanel(): JPanel? {
        return SnykCodeExampleFixesPanel(issue.additionalData)
    }
}

private fun getIssueTitle(issue: ScanIssue) =
    if (issue.additionalData.isSecurityType) {
        issue.title.split(":").firstOrNull() ?: "Unknown issue"
    } else {
        issue.additionalData.message.split(".").firstOrNull() ?: "Unknown issue"
    }

fun defaultFontLabel(labelText: String, bold: Boolean = false): JLabel {
    return JLabel().apply {
        val titleLabelFont: Font? = io.snyk.plugin.ui.getFont(if (bold) Font.BOLD else -1, 14, font)
        titleLabelFont?.let { font = it }
        text = labelText
    }
}
