package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.ui.addRowOfItemsToPanel
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.boldLabel
import snyk.common.lsp.IssueData
import io.snyk.plugin.ui.toolwindow.LabelProvider
import javax.swing.JLabel
import javax.swing.JPanel

class SnykOSSIntroducedThroughPanel(private val ossIssueData: IssueData) : JPanel() {
    private val labelProvider: LabelProvider = LabelProvider()

    init {
        name = "introducedThroughPanel"
        this.layout = GridLayoutManager(11, 2, JBUI.insets(10, 0, 20, 0), 30, -1)

        this.add(
            boldLabel("Vulnerable module:"),
            baseGridConstraintsAnchorWest(2, 0)
        )
        this.add(
            JLabel(ossIssueData.name)/*.apply { this.horizontalAlignment = SwingConstants.LEFT }*/,
            baseGridConstraintsAnchorWest(2, 1, indent = 0)
        )

        val introducedThroughListPanel = getIntroducedThroughListPanel()
        if (introducedThroughListPanel != null) {
            this.add(
                boldLabel("Introduced through:"),
                baseGridConstraintsAnchorWest(3, 0)
            )
            this.add(
                introducedThroughListPanel,
                baseGridConstraintsAnchorWest(3, 1, indent = 0)
            )
        }

        val fixedInText = ossIssueData.fixedIn?.let {
            if (it.isNotEmpty()) {
                it.joinToString(prefix = "${ossIssueData.name}@", separator = ", @")
            } else "Not fixed"
        }
        if (fixedInText != null) {
            this.add(
                boldLabel("Fixed in:"),
                baseGridConstraintsAnchorWest(4, 0)
            )
            this.add(
                JLabel(fixedInText),
                baseGridConstraintsAnchorWest(4, 1, indent = 0)
            )
        }

        val exploit = ossIssueData.exploit
        if (exploit != null) {
            this.add(
                boldLabel("Exploit maturity:"),
                baseGridConstraintsAnchorWest(5, 0)
            )
            this.add(
                JLabel(exploit),
                baseGridConstraintsAnchorWest(5, 1, indent = 0)
            )
        }
    }

    private fun getIntroducedThroughListPanel(): JPanel? {
        val intros = ossIssueData.matchingIssues
            .mapNotNull { vulnerability ->
                vulnerability.from.let { if (it.size > 1) it[1] else null }
            }
            .distinct()

        if (intros.isEmpty()) return null

        val panel = JPanel()
        val packageManager = ossIssueData.packageManager

        panel.layout = GridLayoutManager(1, intros.size * 2, JBUI.emptyInsets(), 0, 0)

        addRowOfItemsToPanel(
            panel = panel,
            startingColumn = 0,
            items = intros.map { item -> labelProvider.getDependencyLabel(packageManager, item) },
            separator = ", ",
            firstSeparator = false,
            opaqueSeparator = false
        )
        return panel
    }
}
