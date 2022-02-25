package snyk.iac

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.boldLabel
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.getFont
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import io.snyk.plugin.ui.insertTitleAndResizableTextIntoPanelColumns
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.IssueDescriptionPanel
import io.snyk.plugin.ui.toolwindow.LabelProvider
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import snyk.common.IgnoreService
import java.awt.Component
import java.awt.Font
import java.awt.Insets
import java.net.MalformedURLException
import java.net.URL
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

class IacSuggestionDescriptionPanel(
    val issue: IacIssue,
    val psiFile: PsiFile?,
    val project: Project
) : JPanel(), IssueDescriptionPanel {

    private val labelProvider = LabelProvider()

    init {
        this.name = "IacSuggestionDescriptionPanel"
        this.layout = GridLayoutManager(10, 1, Insets(20, 10, 20, 20), -1, 10)

        this.add(
            Spacer(),
            baseGridConstraints(
                row = 9,
                fill = GridConstraints.FILL_VERTICAL,
                hSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
                vSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        this.add(titlePanel(), panelGridConstraints(0))

        this.add(
            mainBodyPanel(), panelGridConstraints(1)
        )

        if (!issue.resolve.isNullOrBlank()) {
            this.add(
                remediationPanelWithTitle(issue.resolve), panelGridConstraints(6)
            )
        }

        val referencePanel = addIssueReferences()
        if (referencePanel != null) {
            this.add(referencePanel, panelGridConstraints(row = 7))
        }
    }

    private fun mainBodyPanel(): JPanel {
        val mainBodyPanel = JPanel()
        mainBodyPanel.layout = GridLayoutManager(11, 2, Insets(20, 0, 20, 0), 50, -1)

        insertTitleAndResizableTextIntoPanelColumns(
            panel = mainBodyPanel,
            row = 0,
            title = "Description",
            htmlText = issue.issue
        )

        insertTitleAndResizableTextIntoPanelColumns(
            panel = mainBodyPanel,
            row = 1,
            title = "Impact",
            htmlText = issue.impact
        )

        insertTitleAndResizableTextIntoPanelColumns(
            panel = mainBodyPanel,
            row = 2,
            title = "Path",
            htmlText = issue.path.joinToString(" > "),
            textFont = getFont(-1, -1, JTextArea().font) ?: UIUtil.getLabelFont()
        )

        return mainBodyPanel
    }

    private fun titlePanel(): JPanel {
        val titlePanel = JPanel()
        titlePanel.layout = GridLayoutManager(2, 2, Insets(0, 0, 0, 0), -1, 5)
        val titleLabel = JLabel().apply {
            font = getFont(Font.BOLD, 20, font)
            text = " " + issue.title.ifBlank {
                when (issue.severity) {
                    Severity.CRITICAL -> "Critical Severity"
                    Severity.HIGH -> "High Severity"
                    Severity.MEDIUM -> "Medium Severity"
                    Severity.LOW -> "Low Severity"
                    else -> ""
                }
            }
            icon = SnykIcons.getSeverityIcon(issue.severity, SnykIcons.IconSize.SIZE24)
        }

        titlePanel.add(
            titleLabel,
            baseGridConstraintsAnchorWest(0)
        )

        titlePanel.add(cwePanel(), baseGridConstraintsAnchorWest(row = 1, column = 0, indent = 0))
        titlePanel.add(
            topButtonPanel(),
            baseGridConstraints(row = 0, column = 1, anchor = GridConstraints.ANCHOR_EAST, indent = 0)
        )

        return titlePanel
    }

    private fun topButtonPanel(): Component {
        val panel = JPanel()

        panel.layout = GridLayoutManager(1, 1, Insets(0, 0, 0, 0), 5, 0)

        createIgnoreButton(panel)
        return panel
    }

    private fun createIgnoreButton(panel: JPanel) {
        val ignoreButton = JButton().apply {
            if (issue.ignored) {
                text = IgnoreButtonActionListener.IGNORED_ISSUE_BUTTON_TEXT
                isEnabled = false
            } else {
                text = "Ignore This Issue"
                addActionListener(IgnoreButtonActionListener(IgnoreService(project), issue, psiFile, project))
            }
            name = "ignoreButton"
        }
        panel.add(
            ignoreButton,
            baseGridConstraintsAnchorWest(0)
        )
    }

    private fun cwePanel() = descriptionHeaderPanel(
        issueNaming = "Issue",
        id = issue.id,
        idUrl = issue.documentation
    )

    private fun remediationPanel(resolve: String): JPanel {
        val remediationPanel = JPanel()
        remediationPanel.layout = GridLayoutManager(2, 1, Insets(0, 10, 20, 0), -1, -1)
        remediationPanel.background = UIUtil.getTextFieldBackground()

        val resolveMarkdown = markdownToHtml(resolve)
        val whiteBox = getReadOnlyClickableHtmlJEditorPane(
            resolveMarkdown
        ).apply {
            isOpaque = false
        }

        remediationPanel.add(whiteBox, panelGridConstraints(row = 1))

        return remediationPanel
    }

    private fun addIssueReferences(): JPanel? {
        if (issue.references.isNotEmpty()) {
            val panel = JPanel()
            panel.layout = GridLayoutManager(
                issue.references.size + 2, 1, Insets(20, 0, 20, 0), 50, -1
            )

            panel.add(boldLabel("References"), baseGridConstraintsAnchorWest(row = 1))
            issue.references.forEachIndexed { index, s ->
                val label = try {
                     labelProvider.createLinkLabel(URL(s), s)
                } catch (e: MalformedURLException) {
                    JLabel(s)
                }
                panel.add(label, baseGridConstraintsAnchorWest(2 + index))
            }
            return panel
        }
        return null
    }

    private fun markdownToHtml(sourceStr: String): String {
        val parser = Parser.builder().build()
        val document = parser.parse(sourceStr)

        val renderer = HtmlRenderer.builder().escapeHtml(true).build()

        return renderer.render(document)
    }

    private fun remediationPanelWithTitle(remediation: String): JPanel {
        val remediationPanel = JPanel()
        remediationPanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), 50, -1)

        remediationPanel.add(
            boldLabel("Remediation"),
            baseGridConstraintsAnchorWest(row = 0)
        )

        remediationPanel.add(
            remediationPanel(remediation),
            panelGridConstraints(row = 1, indent = 1)
        )

        return remediationPanel
    }
}
