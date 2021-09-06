package snyk.container.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import snyk.container.ContainerIssue
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import javax.swing.JComponent
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
                9,
                anchor = GridConstraints.ANCHOR_CENTER,
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
            baseGridConstraints(0)
        )
        titlePanel.add(
            cwePanel(),
            baseGridConstraints(1, indent = 0)
        )

        return titlePanel
    }

    private fun cwePanel(): JPanel {
        val panel = JPanel()
        val cwe = issue.identifiers?.cwe ?: emptyArray()
        val cve = issue.identifiers?.cve ?: emptyArray()

        val columnCount = 1 +
            cwe.size * 2 +
            cve.size * 2 +
            2 + // cvss
            2 // description
        panel.layout = GridLayoutManager(1, columnCount, Insets(0, 0, 0, 0), 5, 0)

        panel.add(
            JLabel("Vulnerability"),
            baseGridConstraints(0)
        )

        var lastColumn = addRowOfItemsToPanel(panel, 0, cwe) { "https://cwe.mitre.org/data/definitions/${it.removePrefix("CWE-")}.html" }
        lastColumn = addRowOfItemsToPanel(panel, lastColumn, cve) { "https://cve.mitre.org/cgi-bin/cvename.cgi?name=$it" }

        val cvssScore = issue.cvssScore
        if (cvssScore != null) {
            lastColumn = addRowOfItemsToPanel(panel, lastColumn, arrayOf("CVSS $cvssScore")) { "https://www.first.org/cvss/calculator/3.1#$cvssScore" }
        }

        addRowOfItemsToPanel(panel, lastColumn, arrayOf(issue.id.toUpperCase())) {
            "https://app.snyk.io/vuln/${issue.id}"
        }

        return panel
    }

    private fun mainPanel(): JPanel {
        val panel = JPanel(GridLayoutManager(11, 2, Insets(20, 0, 20, 0), 50, -1))

        fun boldLabel(text: String) = JLabel(text).apply { font = io.snyk.plugin.ui.getFont(Font.BOLD, -1, JLabel().font) }

        val introducedThroughPanel = introducedThroughPanel()
        if (introducedThroughPanel != null) {
            panel.add(
                boldLabel("Introduced through:"),
                baseGridConstraints(3, 0)
            )
            panel.add(
                introducedThroughPanel,
                baseGridConstraints(3, 1)
            )
        }

        if (!issue.nearestFixedInVersion.isNullOrBlank()) {
            panel.add(
                boldLabel("Fixed in: "),
                baseGridConstraints(4, 0)
            )
            panel.add(
                JLabel(issue.nearestFixedInVersion),
                baseGridConstraints(4, 1)
            )
        }

        return panel
    }

    private fun introducedThroughPanel(): JPanel? {
        val introducedThrough = issue.from
        if (introducedThrough.isEmpty()) {
            return null
        }

        val panel = JPanel(GridLayoutManager(1, introducedThrough.size * 2, Insets(0, 0, 0, 0), 0, 0))

        if (introducedThrough.isNotEmpty()) {
            addRowOfItemsToPanel(
                panel = panel,
                startingColumn = 0,
                items = introducedThrough,
                separator = ", ",
                firstSeparator = false,
                opaqueSeparator = false
            ) { "https://app.snyk.io/test/${it}" }
        }
        return panel
    }

    private fun overviewPanel(): JPanel {
        val panel = JPanel(GridLayoutManager(2, 2, Insets(0, 5, 0, 0), -1, 0))

        val descriptionMarkdown = issue.description
        val document = Parser.builder().build().parse(descriptionMarkdown)
        val descriptionHtml = HtmlRenderer.builder().escapeHtml(false).build().render(document)

        val descriptionPane = getReadOnlyClickableHtmlJEditorPane(descriptionHtml)
        panel.add(
            descriptionPane,
            panelGridConstraints(1)
        )

        return panel
    }

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

    private fun panelGridConstraints(row: Int) = baseGridConstraints(
        row = row,
        anchor = GridConstraints.ANCHOR_CENTER,
        fill = GridConstraints.FILL_BOTH,
        HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        indent = 0
    )

    private fun defaultFontLabel(labelText: String, bold: Boolean = false): JLabel {
        return JLabel().apply {
            val titleLabelFont: Font? = io.snyk.plugin.ui.getFont(if (bold) Font.BOLD else -1, 14, font)
            titleLabelFont?.let { font = it }
            text = labelText
        }
    }

    private fun addRowOfItemsToPanel(
        panel: JPanel,
        startingColumn: Int,
        items: Array<String>,
        separator: String = " | ",
        firstSeparator: Boolean = true,
        opaqueSeparator: Boolean = true,
        buildUrl: (String) -> String
    ): Int {
        var currentColumn = startingColumn
        items.forEachIndexed() { index, item ->
            if (item.isNotEmpty()) {
                val positionLabel = LinkLabel.create(item) {
                    val url = buildUrl(item)
                    BrowserUtil.open(url)
                }
                if (currentColumn != startingColumn || firstSeparator) {
                    currentColumn++
                    panel.add(
                        JLabel(separator).apply { if (opaqueSeparator) makeOpaque(this, 50) },
                        baseGridConstraints(0, column = currentColumn, indent = 0)
                    )
                }
                currentColumn++
                panel.add(positionLabel, baseGridConstraints(0, column = currentColumn, indent = 0))
            }
        }
        return currentColumn
    }

    private fun makeOpaque(component: JComponent, alpha: Int) {
        component.foreground = Color(
            component.foreground.red,
            component.foreground.green,
            component.foreground.blue,
            alpha
        )
    }
}
