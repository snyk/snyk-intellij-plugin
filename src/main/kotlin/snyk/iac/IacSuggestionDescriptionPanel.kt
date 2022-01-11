package snyk.iac

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.HyperlinkLabel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.ui.getFont
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import io.snyk.plugin.ui.toolwindow.LabelProvider
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import snyk.common.IgnoreService
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.net.URL
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.event.HyperlinkEvent

class IacSuggestionDescriptionPanel(
    val issue: IacIssue,
    val psiFile: PsiFile?,
    val project: Project
) : JPanel() {

    private val labelProvider = LabelProvider()

    private fun baseGridConstraints(
        row: Int,
        column: Int = 0,
        rowSpan: Int = 1,
        colSpan: Int = 1,
        anchor: Int = GridConstraints.ANCHOR_WEST,
        fill: Int = GridConstraints.FILL_NONE,
        HSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
        VSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
        minimumSize: Dimension? = null,
        preferredSize: Dimension? = null,
        maximumSize: Dimension? = null,
        indent: Int = 1,
        useParentLayout: Boolean = false
    ): GridConstraints {
        return GridConstraints(
            row, column, rowSpan, colSpan, anchor, fill, HSizePolicy, VSizePolicy, minimumSize, preferredSize,
            maximumSize, indent, useParentLayout
        )
    }

    private fun panelGridConstraints(
        row: Int,
        column: Int = 0,
        anchor: Int = GridConstraints.ANCHOR_CENTER,
        indent: Int = 1
    ) = baseGridConstraints(
        row = row,
        column = column,
        anchor = anchor,
        fill = GridConstraints.FILL_BOTH,
        HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        indent = indent
    )

    init {
        this.name = "IacSuggestionDescriptionPanel"
        this.layout = GridLayoutManager(10, 1, Insets(20, 10, 20, 20), -1, 10)

        this.add(
            Spacer(),
            baseGridConstraints(
                9,
                anchor = GridConstraints.ANCHOR_CENTER,
                fill = GridConstraints.FILL_VERTICAL,
                HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
                VSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        this.add(titlePanel(), panelGridConstraints(0))

        this.add(
            mainBodyPanel(),
            panelGridConstraints(1)
        )

        if (!issue.resolve.isNullOrBlank()) {
            this.add(
                remediationPanelWithTitle(issue.resolve),
                panelGridConstraints(6)
            )
        }
        if (issue.references.isNotEmpty()) {
            this.add(addIssueReferences(), panelGridConstraints(row = 7))
        }
    }

    private fun boldLabel(text: String) = JLabel(text).apply {
        font = getFont(Font.BOLD, -1, JLabel().font)
    }

    private fun mainBodyPanel(): JPanel {
        val mainBodyPanel = JPanel()

        mainBodyPanel.layout = GridLayoutManager(11, 2, Insets(20, 0, 20, 0), 50, -1)

        mainBodyPanel.add(
            boldLabel("Description"),
            baseGridConstraints(0, 0)
        )
        mainBodyPanel.add(
            getReadOnlyClickableHtmlJEditorPane(issue.issue),
            panelGridConstraints(0, 1)
        )

        mainBodyPanel.add(
            boldLabel("Impact"),
            baseGridConstraints(1, 0)
        )
        mainBodyPanel.add(
            getReadOnlyClickableHtmlJEditorPane(issue.impact),
            panelGridConstraints(1, 1)
        )

        mainBodyPanel.add(
            boldLabel("Path"),
            baseGridConstraints(2, 0)
        )

        val font = getFont(-1, -1, JTextArea().font) ?: UIUtil.getLabelFont()
        val pathLabel = getReadOnlyClickableHtmlJEditorPane(issue.path.joinToString(" > "), font)

        mainBodyPanel.add(
            pathLabel,
            panelGridConstraints(2, 1)
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
            baseGridConstraints(0)
        )

        titlePanel.add(cwePanel(), baseGridConstraints(row = 1, column = 0, indent = 0))
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
            baseGridConstraints(0)
        )
    }

    private fun cwePanel(): Component {
        val panel = JPanel()

        panel.layout = GridLayoutManager(1, 3, Insets(0, 0, 0, 0), 5, 0)

        panel.add(
            JLabel("Issue"),
            baseGridConstraints(0)
        )

        panel.add(
            defaultFontLabel(" | ").apply {
                this.foreground = Color(this.foreground.red, this.foreground.green, this.foreground.blue, 50)
            },
            baseGridConstraints(0, column = 1, indent = 0)
        )

        val positionLabel = linkLabel(
            linkText = issue.id,
            toolTipText = "Click to open description in the Browser"
        ) {
            val url = issue.documentation
            BrowserUtil.open(url)
        }

        panel.add(positionLabel, baseGridConstraints(0, 2, indent = 0))

        return panel
    }

    private fun linkLabel(
        linkText: String,
        toolTipText: String,
        customFont: Font? = null,
        onClick: (HyperlinkEvent) -> Unit
    ): HyperlinkLabel {
        return HyperlinkLabel().apply {
            this.setHyperlinkText("", linkText, "")
            this.toolTipText = toolTipText
            this.font = getFont(-1, 14, customFont ?: font)
            addHyperlinkListener {
                onClick.invoke(it)
            }
        }
    }

    private fun defaultFontLabel(labelText: String, bold: Boolean = false): JLabel {
        return JLabel().apply {
            val titleLabelFont: Font? = getFont(if (bold) Font.BOLD else -1, 14, font)
            titleLabelFont?.let { font = it }
            text = labelText
        }
    }

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

    private fun addIssueReferences(): JPanel {
        val panel = JPanel()
        panel.layout = GridLayoutManager(
            issue.references.size + 1,
            1,
            Insets(20, 0, 20, 0),
            50,
            -1
        )

        panel.add(boldLabel("References"), baseGridConstraints(row = 1))
        issue.references.forEachIndexed { index, s ->
            val label = labelProvider.createLinkLabel(URL(s), s)
            panel.add(label, baseGridConstraints(2 + index))
        }
        return panel
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
            baseGridConstraints(
                row = 0
            )
        )

        remediationPanel.add(
            remediationPanel(remediation),
            panelGridConstraints(
                row = 1
            )
        )

        return remediationPanel
    }
}
