package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.ui.components.ActionLink
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.boldLabel
import io.snyk.plugin.ui.insertTitleAndResizableTextIntoPanelColumns
import io.snyk.plugin.ui.panelGridConstraints
import snyk.common.lsp.IssueData
import javax.swing.JPanel

class SnykOSSDetailedPathsPanel(private val ossIssueData: IssueData) : JPanel() {
    init {
        name = "detailedPathsPanel"
        this.layout = GridLayoutManager(2, 2, JBUI.emptyInsets(), -1, -1)

        this.add(
            boldLabel("Detailed paths").apply {
                font = font.deriveFont(14f)
            },
            baseGridConstraintsAnchorWest(
                row = 0
            )
        )

        this.add(
            getInnerDetailedPathsPanel(3),
            panelGridConstraints(
                row = 1
            )
        )
    }

    private fun getInnerDetailedPathsPanel(itemsToShow: Int? = null): JPanel {
        val detailsPanel = JPanel()
        detailsPanel.layout = GridLayoutManager(ossIssueData.matchingIssues.size + 2, 2, JBUI.emptyInsets(), -1, -1)

        ossIssueData.matchingIssues
            .take(itemsToShow ?: ossIssueData.matchingIssues.size)
            .forEachIndexed { index, vuln ->
                val detailPanel = JPanel()
                detailPanel.layout = GridLayoutManager(2, 2, JBUI.emptyInsets(), 30, 0)

                insertTitleAndResizableTextIntoPanelColumns(
                    panel = detailPanel,
                    row = 0,
                    title = "Introduced through:",
                    htmlText = vuln.from.joinToString(separator = " > ")
                )

                val remediationText = when {
                    vuln.upgradePath.isEmpty() || vuln.upgradePath.size < 2 -> "none"
                    else -> "Upgrade to " + vuln.upgradePath.first()
                }
                insertTitleAndResizableTextIntoPanelColumns(
                    panel = detailPanel,
                    row = 1,
                    title = "Fix:",
                    htmlText = remediationText
                )

                detailsPanel.add(
                    detailPanel,
                    panelGridConstraints(
                        row = index + 1
                    )
                )
            }

        if (itemsToShow != null && itemsToShow < ossIssueData.matchingIssues.size) {
            val showMoreLabel = ActionLink("...and ${ossIssueData.matchingIssues.size - itemsToShow} more") {
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
                baseGridConstraintsAnchorWest(ossIssueData.matchingIssues.size + 1)
            )
        }

        return detailsPanel
    }
}
