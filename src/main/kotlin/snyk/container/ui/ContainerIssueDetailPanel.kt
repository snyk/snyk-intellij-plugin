package snyk.container.ui

import com.intellij.ui.components.labels.LinkLabel
import com.intellij.uiDesigner.core.GridLayoutManager
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.boldLabel
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import io.snyk.plugin.ui.insertTitleAndResizableTextIntoPanelColumns
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import snyk.container.ContainerIssue
import java.awt.Insets
import javax.swing.JPanel

class ContainerIssueDetailPanel(
    private val groupedVulns: Collection<ContainerIssue>
) : IssueDescriptionPanelBase(title = groupedVulns.first().title, severity = groupedVulns.first().getSeverity()) {

    private val issue = groupedVulns.first()

    init {
        createUI()
    }

    override fun createMainBodyPanel(): Pair<JPanel, Int> {
        val lastRowToAddSpacer = 4
        val panel = JPanel(
            GridLayoutManager(lastRowToAddSpacer + 1, 1, Insets(10, 10, 20, 20), -1, 10)
        )

        panel.add(
            mainPanel(),
            panelGridConstraints(1)
        )

        panel.add(
            getDetailedPathsPanel(),
            panelGridConstraints(2)
        )

        panel.add(
            overviewPanel(),
            panelGridConstraints(3)
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
            GridLayoutManager(2, 2, Insets(0, 0, 0, 0), 30, -1)
        )

        val introducedThrough = groupedVulns
            .mapNotNull { vulnerability ->
                vulnerability.from.let { if (it.size > 1) it[1] else null }
            }
            .distinct()
        if (introducedThrough.isNotEmpty()) {
            insertTitleAndResizableTextIntoPanelColumns(
                panel = panel,
                row = 0,
                title = "Introduced through:",
                htmlText = introducedThrough.joinToString(separator = ", ")
            )
        }

        val fixedInText = issue.nearestFixedInVersion ?: "Not fixed"
        insertTitleAndResizableTextIntoPanelColumns(
            panel = panel,
            row = 1,
            title = "Fixed in:",
            htmlText = fixedInText
        )

        return panel
    }

    private fun getDetailedPathsPanel(): JPanel {
        val detailsPanel = JPanel()
        detailsPanel.layout = GridLayoutManager(2, 2, Insets(20, 0, 0, 0), -1, -1)

        detailsPanel.add(
            boldLabel("Detailed paths").apply {
                font = font.deriveFont(14f)
            },
            baseGridConstraintsAnchorWest(
                row = 0
            )
        )

        detailsPanel.add(
            getInnerDetailedPathsPanel(3),
            panelGridConstraints(
                row = 1
            )
        )

        return detailsPanel
    }

    private fun getInnerDetailedPathsPanel(itemsToShow: Int? = null): JPanel {
        val detailsPanel = JPanel()
        detailsPanel.layout = GridLayoutManager(groupedVulns.size + 2, 2, Insets(0, 0, 0, 0), -1, -1)

        groupedVulns
            .take(itemsToShow ?: groupedVulns.size)
            .forEachIndexed { index, vuln ->
                val detailPanel = JPanel()
                detailPanel.layout = GridLayoutManager(2, 2, Insets(0, 0, 0, 0), 30, 5)

                insertTitleAndResizableTextIntoPanelColumns(
                    panel = detailPanel,
                    row = 0,
                    title = "Introduced through:",
                    htmlText = vuln.from.joinToString(separator = " > ")
                )

                insertTitleAndResizableTextIntoPanelColumns(
                    panel = detailPanel,
                    row = 1,
                    title = "Fix:",
                    htmlText = vuln.nearestFixedInVersion ?: "none"
                )

                detailsPanel.add(
                    detailPanel,
                    panelGridConstraints(
                        row = index + 1
                    )
                )
            }

        if (itemsToShow != null && itemsToShow < groupedVulns.size) {
            val showMoreLabel = LinkLabel.create("...and ${groupedVulns.size - itemsToShow} more") {
                detailsPanel.removeAll()
                detailsPanel.add(
                    getInnerDetailedPathsPanel(),
                    panelGridConstraints(
                        row = 0
                    )
                )
            }
            detailsPanel.add(
                showMoreLabel,
                baseGridConstraintsAnchorWest(groupedVulns.size + 1)
            )
        }

        return detailsPanel
    }

    private fun overviewPanel(): JPanel {
        val panel = JPanel(GridLayoutManager(2, 2, Insets(10, 5, 0, 0), -1, 0))

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
