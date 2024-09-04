package io.snyk.plugin.ui.toolwindow.panels

import io.snyk.plugin.ui.jcef.GenerateAIFixHandler
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

                val generateAIFixHandler = GenerateAIFixHandler(snykFile.project)
                loadHandlerGenerators += {
                    generateAIFixHandler.generateAIFixCommand(it)
                }

            }
            val html = this.getCustomCssAndScript()
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

    fun getCustomCssAndScript(): String {
        var html = issue.details()
        val ideScript = getCustomScript()
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
        html = html.replace("\${ideScript}", "<script nonce=\${nonce}>$ideScript</script>")

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

    private fun getCustomScript(): String {
        return """
            (function () {
              function toggleElement(element, toggle) {
                if (!element) return;

                if (toggle === "show") {
                  element.classList.remove("hidden");
                } else if (toggle === "hide") {
                  element.classList.add("hidden");
                } else {
                  console.error("Unexpected toggle value", toggle);
                }
              }

              let diffSelectedIndex = 0;

              const generateAiFixBtn = document.getElementById("generate-ai-fix");
              const loadingIndicator = document.getElementById("fix-loading-indicator");
              const fixWrapperElem = document.getElementById("fix-wrapper");
              const fixSectionElem = document.getElementById("fixes-section");

              const diffSelectedIndexElem = document.getElementById("diff-counter");

              const diffTopElem = document.getElementById("diff-top");
              const diffElem = document.getElementById("diff");
              const noDiffsElem = document.getElementById("info-no-diffs");

              const diffNumElem = document.getElementById("diff-number");
              const diffNum2Elem = document.getElementById("diff-number2");

              function generateAIFix() {
                toggleElement(generateAiFixBtn, "hide");
                toggleElement(loadingIndicator, "show");
              }

              function generateDiffHtml(patch) {
                const codeLines = patch.split("\n");

                // the first two lines are the file names
                codeLines.shift();
                codeLines.shift();

                const diffHtml = document.createElement("div");
                let blockDiv = null;

                for (const line of codeLines) {
                  if (line.startsWith("@@ ")) {
                    blockDiv = document.createElement("div");
                    blockDiv.className = "example";

                    if (blockDiv) {
                      diffHtml.appendChild(blockDiv);
                    }
                  } else {
                    const lineDiv = document.createElement("div");
                    lineDiv.className = "example-line";

                    if (line.startsWith("+")) {
                      lineDiv.classList.add("added");
                    } else if (line.startsWith("-")) {
                      lineDiv.classList.add("removed");
                    }

                    const lineCode = document.createElement("code");
                    // if line is empty, we need to fallback to ' '
                    // to make sure it displays in the diff
                    lineCode.innerText = line.slice(1, line.length) || " ";

                    lineDiv.appendChild(lineCode);
                    blockDiv?.appendChild(lineDiv);
                  }
                }

                return diffHtml;
              }

              function getFilePathFromFix(fix) {
                return Object.keys(fix.unifiedDiffsPerFile)[0];
              }

              function showCurrentDiff(fixes) {
                toggleElement(diffTopElem, "show");
                toggleElement(diffElem, "show");
                toggleElement(noDiffsElem, "hide");

                diffNumElem.innerText = fixes.length.toString();
                diffNum2Elem.innerText = fixes.length.toString();

                diffSelectedIndexElem.innerText = (diffSelectedIndex + 1).toString();

                const diffSuggestion = fixes[diffSelectedIndex];
                const filePath = getFilePathFromFix(diffSuggestion);
                const patch = diffSuggestion.unifiedDiffsPerFile[filePath];

                // clear all elements
                while (diffElem.firstChild) {
                  diffElem.removeChild(diffElem.firstChild);
                }
                diffElem.appendChild(generateDiffHtml(patch));
              }

              function showAIFixes(fixes) {
                toggleElement(fixSectionElem, "show");
                toggleElement(loadingIndicator, "hide");
                toggleElement(fixWrapperElem, "hide");

                showCurrentDiff(fixes);
              }

              generateAiFixBtn?.addEventListener("click", generateAIFix);

              // This function will be called once the response is received from the LS
              window.receiveAIFixResponse = function (fixes) {
                showAIFixes(fixes);
              };
            })();
        """.trimIndent()
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
