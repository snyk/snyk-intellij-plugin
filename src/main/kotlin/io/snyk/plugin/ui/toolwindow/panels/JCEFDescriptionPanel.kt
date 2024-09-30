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
import io.snyk.plugin.ui.jcef.ApplyFixHandler
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

                val generateAIFixHandler = GenerateAIFixHandler()
                loadHandlerGenerators += {
                    generateAIFixHandler.generateAIFixCommand(it)
                }

                val applyFixHandler = ApplyFixHandler(snykFile.project)
                loadHandlerGenerators += {
                    applyFixHandler.generateApplyFixCommand(it)
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
              // Utility function to show/hide an element based on a toggle value
              function toggleElement(element, action) {
                if (!element) return;
                element.classList.toggle("hidden", action === "hide");
              }

              function nextDiff() {
                  if (!fixes || diffSelectedIndex >= fixes.length - 1) return;
                  ++diffSelectedIndex;
                  showCurrentDiff(fixes);
              }

              function previousDiff() {
                  if (!fixes || diffSelectedIndex <= 0) return;
                  --diffSelectedIndex;
                  showCurrentDiff(fixes)
              }

              // Generate HTML for the code diff from a patch
              function generateDiffHtml(patch) {
                const codeLines = patch.split("\n");
                codeLines.splice(0, 2); // Skip the first two lines (file paths)

                const diffHtml = document.createElement("div");
                let blockDiv = null;

                codeLines.forEach(line => {
                  if (line.startsWith("@@ ")) {
                    // Start a new block for a diff hunk
                    blockDiv = document.createElement("div");
                    blockDiv.className = "example";
                    diffHtml.appendChild(blockDiv);
                  } else {
                    // Generate a line div and apply the appropriate class based on addition/removal
                    const lineDiv = document.createElement("div");
                    lineDiv.className = "example-line";
                    if (line.startsWith("+")) {
                      lineDiv.classList.add("added");
                    } else if (line.startsWith("-")) {
                      lineDiv.classList.add("removed");
                    }

                    // Create a <code> block for the line content
                    const lineCode = document.createElement("code");
                    lineCode.innerText = line.slice(1) || " "; // Ensure empty lines display properly
                    lineDiv.appendChild(lineCode);

                    blockDiv?.appendChild(lineDiv); // Append the line to the current block
                  }
                });

                return diffHtml;
              }

              // Extract the file path from the AI fix object
              function getFilePathFromFix(fix) {
                return Object.keys(fix.unifiedDiffsPerFile)[0];
              }

              // Show the diff for the currently selected AI fix
              function showCurrentDiff(fixes) {
                toggleElement(diffTopElem, "show");
                toggleElement(diffElem, "show");
                toggleElement(noDiffsElem, "hide");

                const totalFixes = fixes.length;
                const currentFix = fixes[diffSelectedIndex];
                const filePath = getFilePathFromFix(currentFix);
                const patch = currentFix.unifiedDiffsPerFile[filePath];

                // Update diff counters
                diffNumElem.innerText = totalFixes.toString();
                diffNum2Elem.innerText = totalFixes.toString();
                diffSelectedIndexElem.innerText = (diffSelectedIndex + 1).toString();

                // Clear and update the diff container
                diffElem.innerHTML = ''; // Clear previous diff
                diffElem.appendChild(generateDiffHtml(patch));
              }

              // Show the AI fixes received from the Language Server
              function showAIFixes(fixes) {
                toggleElement(fixSectionElem, "show");
                toggleElement(fixLoadingIndicatorElem, "hide");
                toggleElement(fixWrapperElem, "hide");

                showCurrentDiff(fixes);
              }

              // Handle AI fix generation button click
              function generateAIFix() {
                toggleElement(generateAiFixBtn, "hide");
                toggleElement(fixLoadingIndicatorElem, "show");
              }

              // Handle AI fix re-generation button click
              function reGenerateAIFix() {
                toggleElement(fixErrorSectionElem, "hide");
                toggleElement(fixWrapperElem, "show");

                generateAIFix()
              }

              function showGenerateAIFixError() {
                toggleElement(fixLoadingIndicatorElem, "hide");
                toggleElement(fixWrapperElem, "hide");
                toggleElement(fixSectionElem, "hide");
                toggleElement(fixErrorSectionElem, "show");
              }

              function applyFix() {
                console.log('Applying fix', fixes);
                if (!fixes.length) return;

                const currentFix = fixes[diffSelectedIndex];
                const fixId = currentFix.fixId;
                const filePath = getFilePathFromFix(currentFix);
                const patch = currentFix.unifiedDiffsPerFile[filePath];


                window.applyFixQuery(fixId + '|@' + filePath + '|@' + patch);

                // Following VSCode logic, the steps are:
                // 1. Read the current file content.
                // 2. Apply a patch to that content.
                // 3. Edit the file in the workspace.
                // 4. Highlight the added code.
                // 5. Setup close or save events.
                 console.log('Applying fix', patch);
              }

              // DOM element references
              const generateAiFixBtn = document.getElementById("generate-ai-fix");
              const applyFixBtn = document.getElementById('apply-fix')
              const retryGenerateFixBtn = document.getElementById('retry-generate-fix')

              const fixLoadingIndicatorElem = document.getElementById("fix-loading-indicator");
              const fixWrapperElem = document.getElementById("fix-wrapper");
              const fixSectionElem = document.getElementById("fixes-section");
              const fixErrorSectionElem = document.getElementById("fixes-error-section");

              const nextDiffElem = document.getElementById('next-diff');
              const previousDiffElem = document.getElementById('previous-diff');
              const diffSelectedIndexElem = document.getElementById("diff-counter");
              const diffTopElem = document.getElementById("diff-top");
              const diffElem = document.getElementById("diff");
              const noDiffsElem = document.getElementById("info-no-diffs");

              const diffNumElem = document.getElementById("diff-number");
              const diffNum2Elem = document.getElementById("diff-number2");

              let diffSelectedIndex = 0;
              let fixes = [];

              // Event listener for Generate AI fix button
              generateAiFixBtn?.addEventListener("click", generateAIFix);
              applyFixBtn?.addEventListener('click', applyFix);
              retryGenerateFixBtn?.addEventListener('click', reGenerateAIFix);

              nextDiffElem?.addEventListener("click", nextDiff);
              previousDiffElem?.addEventListener("click", previousDiff);

              // This function will be called once the response is received from the Language Server
              window.receiveAIFixResponse = function (fixesResponse) {
                fixes = [...fixesResponse];
                if (!fixes.length) {
                  showGenerateAIFixError();
                  return;
                }
                showAIFixes(fixes);
              };

              window.receiveApplyFixResponse = function (success) {
              console.log('[[receiveApplyFixResponse]]', success);
                if (success) {
                    applyFixBtn.disabled = true;
                    console.log('Fix applied', success);
                    document.getElementById('apply-fix').disabled = true;
                } else {
                    console.error('Failed to apply fix', success);
                }
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
