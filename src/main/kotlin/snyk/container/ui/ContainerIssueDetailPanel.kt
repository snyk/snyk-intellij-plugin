package snyk.container.ui

import com.intellij.uiDesigner.core.GridLayoutManager
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import io.snyk.plugin.ui.insertTitleAndResizableTextIntoPanelColumns
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.IssueDescriptionPanelBase
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import snyk.container.ContainerIssue
import java.awt.Insets
import javax.swing.JPanel

class ContainerIssueDetailPanel(
    private val issue: ContainerIssue
) : IssueDescriptionPanelBase(title = issue.title, severity = issue.severity) {

    init {
        createUI()
    }

    override fun createMainBodyPanel(): Pair<JPanel, Int> {
        val lastRowToAddSpacer = 3
        val panel = JPanel(
            GridLayoutManager(lastRowToAddSpacer + 1, 1, Insets(10, 10, 20, 20), -1, 10)
        )

        panel.add(
            mainPanel(),
            panelGridConstraints(1)
        )

        panel.add(
            overviewPanel(),
            panelGridConstraints(2)
        )

        return Pair(panel, lastRowToAddSpacer)
    }

    override fun secondRowTitlePanel(): DescriptionHeaderPanel = descriptionHeaderPanel(
        issueNaming = "Vulnerability",
        cwes = issue.identifiers?.cwe ?: emptyList(),
        cves = issue.identifiers?.cve ?: emptyList(),
        cvssScore = issue.cvssScore,
        cvssV3 = issue.cvssV3,
        id = issue.id
    )

    private fun mainPanel(): JPanel {
        val panel = JPanel(
            GridLayoutManager(2, 2, Insets(0,0, 0, 0), 50, -1)
        )

        val introducedThrough = issue.from
        if (introducedThrough.isNotEmpty()) {
            insertTitleAndResizableTextIntoPanelColumns(
                panel = panel,
                row = 0,
                title = "Introduced through:",
                htmlText = introducedThrough.joinToString(separator = ", ")
            )
        }

        val fixedInText = issue.nearestFixedInVersion
        if (!fixedInText.isNullOrBlank()) {
            insertTitleAndResizableTextIntoPanelColumns(
                panel = panel,
                row = 1,
                title = "Fixed in:",
                htmlText = fixedInText
            )
        }

        return panel
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
