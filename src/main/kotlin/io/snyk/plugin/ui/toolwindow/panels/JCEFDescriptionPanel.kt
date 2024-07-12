package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.SnykFile
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.LoadHandlerGenerator
import io.snyk.plugin.ui.jcef.OpenFileLoadHandlerGenerator
import io.snyk.plugin.ui.jcef.ThemeBasedStylingGenerator
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import io.snyk.plugin.ui.wrapWithScrollPane
import snyk.common.ProductType
import snyk.common.lsp.ScanIssue
import stylesheets.SnykStylesheets
import java.awt.BorderLayout
import java.awt.Font
import java.nio.file.Paths
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.collections.set

class SuggestionDescriptionPanelFromLS(
    snykFile: SnykFile,
    private val issue: ScanIssue,
) : IssueDescriptionPanelBase(
        title = issue.title(),
        severity = issue.getSeverityAsEnum(),
    ) {
    val project = snykFile.project
    private val unexpectedErrorMessage =
        "Snyk encountered an issue while rendering the vulnerability description. Please try again, or contact support if the problem persists. We apologize for any inconvenience caused."

    init {
        if (issue.canLoadSuggestionPanelFromHTML()) {
            val loadHandlerGenerators: MutableList<LoadHandlerGenerator> =
                emptyList<LoadHandlerGenerator>().toMutableList()

            loadHandlerGenerators += {
                ThemeBasedStylingGenerator().generate(it)
            }

            if (issue.additionalData.getProductType() == ProductType.CODE_SECURITY ||
                issue.additionalData.getProductType() == ProductType.CODE_QUALITY
            ) {
                val virtualFiles = LinkedHashMap<String, VirtualFile?>()
                for (dataFlow in issue.additionalData.dataFlow) {
                    virtualFiles[dataFlow.filePath] =
                        VirtualFileManager.getInstance().findFileByNioPath(Paths.get(dataFlow.filePath))
                }

                val openFileLoadHandlerGenerator = OpenFileLoadHandlerGenerator(snykFile.project, virtualFiles)
                loadHandlerGenerators += {
                    openFileLoadHandlerGenerator.generate(it)
                }
            }
            val html = this.getStyledHTML()
            val jbCefBrowserComponent =
                JCEFUtils.getJBCefBrowserComponentIfSupported(html, loadHandlerGenerators)
            if (jbCefBrowserComponent == null) {
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
                            jbCefBrowserComponent,
                            panelGridConstraints(1),
                        )
                    }
                this.add(
                    wrapWithScrollPane(panel),
                    BorderLayout.CENTER,
                )
                this.add(panel)
            }
        } else {
            createUI()
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
                if (issue.additionalData.getProductType() == ProductType.CODE_SECURITY ||
                    issue.additionalData.getProductType() == ProductType.CODE_QUALITY
                ) {
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
                } else if (issue.additionalData.getProductType() == ProductType.OSS) {
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
                } else {
                    TODO()
                }
            }
        return Pair(panel, lastRowToAddSpacer)
    }

    fun getStyledHTML(): String {
        var html = issue.details()
        var ideStyle = ""
        if (issue.additionalData.getProductType() == ProductType.CODE_SECURITY ||
            issue.additionalData.getProductType() == ProductType.CODE_QUALITY
        ) {
            ideStyle = SnykStylesheets.SnykCodeSuggestion
        } else if (issue.additionalData.getProductType() == ProductType.OSS) {
            ideStyle = SnykStylesheets.SnykOSSSuggestion
        }
        html = html.replace("\${ideStyle}", "<style nonce=\${nonce}>$ideStyle</style>")
        html = html.replace("\${headerEnd}", "")
        html =
            html.replace(
                "\${ideScript}",
                "<script nonce=\${nonce}>" +
                    "    // Ensure the document is fully loaded before executing script to manipulate DOM.\n" +
                    "    document.addEventListener('DOMContentLoaded', () => {\n" +
                    "        document.getElementById(\"ai-fix-wrapper\").classList.add(\"hidden\");\n" +
                    "        document.getElementById(\"no-ai-fix-wrapper\").classList.remove(\"hidden\");\n" +
                    "    })" +
                    "</script>",
            )

        val nonce = getNonce()
        html = html.replace("\${nonce}", nonce)

        return html
    }

    private fun getNonce(): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..32)
            .map { allowedChars.random() }
            .joinToString("")
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
