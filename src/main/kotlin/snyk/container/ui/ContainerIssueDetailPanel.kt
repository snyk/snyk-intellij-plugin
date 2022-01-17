package snyk.container.ui

import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import io.snyk.plugin.ui.panelGridConstraints
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import snyk.container.ContainerIssue
import java.awt.Font
import java.awt.Insets
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel

class ContainerIssueDetailPanel(
    private val issue: ContainerIssue
) : JPanel() {

    init {
        this.layout = GridLayoutManager(10, 1, Insets(20, 10, 20, 20), -1, 10)

        this.add(
            Spacer(),
            baseGridConstraints(
                row = 9,
                fill = GridConstraints.FILL_VERTICAL,
                HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
                VSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        this.add(
            titlePanel(),
            panelGridConstraints(0)
        )

        this.add(
            mainPanel(),
            panelGridConstraints(1)
        )

        this.add(
            overviewPanel(),
            panelGridConstraints(2)
        )

        this.name = "ContainerIssueDetailPanel"
    }

    private fun titlePanel(): JPanel {
        val titlePanel = JPanel()
        titlePanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, 5)

        val titleLabel = JLabel().apply {
            font = io.snyk.plugin.ui.getFont(Font.BOLD, 20, font)
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
        titlePanel.add(
            cwePanel(),
            baseGridConstraintsAnchorWest(1, indent = 0)
        )

        return titlePanel
    }

    private fun cwePanel() = descriptionHeaderPanel(
        issueNaming = "Vulnerability",
        cwes = issue.identifiers?.cwe ?: emptyList(),
        cves = issue.identifiers?.cve ?: emptyList(),
        cvssScore = issue.cvssScore,
        cvsSv3 = issue.CVSSv3,
        id = issue.id
    )

    private fun mainPanel(): JPanel {
        val panel = JPanel(
            GridLayoutManager(11, 2, Insets(20, 0, 20, 0), 50, -1)
        )

        fun boldLabel(text: String) =
            JLabel(text).apply { font = io.snyk.plugin.ui.getFont(Font.BOLD, -1, JLabel().font) }

        val introducedThroughPane = introducedThroughPane()
        if (introducedThroughPane != null) {
            panel.add(
                boldLabel("Introduced through:"),
                baseGridConstraintsAnchorWest(3, 0)
            )
            panel.add(
                introducedThroughPane,
                panelGridConstraints(3, 1)
            )
        }

        if (!issue.nearestFixedInVersion.isNullOrBlank()) {
            panel.add(
                boldLabel("Fixed in: "),
                baseGridConstraintsAnchorWest(4, 0)
            )
            panel.add(
                JLabel(issue.nearestFixedInVersion),
                baseGridConstraintsAnchorWest(4, 1)
            )
        }

        return panel
    }

    private fun introducedThroughPane(): JEditorPane? {
        val introducedThrough = issue.from
        if (introducedThrough.isEmpty()) {
            return null
        }
        val introducedThroughAsHtml: String = introducedThrough.joinToString(separator = ", ") { it }

        return getReadOnlyClickableHtmlJEditorPane(introducedThroughAsHtml)
    }

    private fun overviewPanel(): JPanel {
        val panel = JPanel(GridLayoutManager(2, 2, Insets(0, 5, 0, 0), -1, 0))

        val descriptionMarkdown = issue.description.replaceFirst("## NVD Description", "## Description")
        val document = Parser.builder().build().parse(descriptionMarkdown)
        val descriptionHtml = HtmlRenderer.builder().escapeHtml(false).build().render(document)

        val descriptionPane = getReadOnlyClickableHtmlJEditorPane(descriptionHtml)
        panel.add(
            descriptionPane,
            panelGridConstraints(1)
        )

        return panel
    }
}
