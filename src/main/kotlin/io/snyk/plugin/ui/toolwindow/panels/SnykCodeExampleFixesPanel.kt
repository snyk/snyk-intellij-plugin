package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.icons.AllIcons
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.panelGridConstraints
import snyk.common.lsp.IssueData
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants

class SnykCodeExampleFixesPanel(codeIssueData: IssueData): JPanel() {
    init {
        val fixes = codeIssueData.exampleCommitFixes
        val examplesCount = fixes.size.coerceAtMost(3)

        this.layout = GridLayoutManager(3, 1, JBUI.emptyInsets(), -1, 5)

        this.add(
            defaultFontLabel("External example fixes", true),
            baseGridConstraintsAnchorWest(0)
        )

        val labelText =
            if (examplesCount == 0) {
                "No example fixes available."
            } else {
                "This issue was fixed by ${codeIssueData.repoDatasetSize} projects. Here are $examplesCount example fixes."
            }
        this.add(
            defaultFontLabel(labelText),
            baseGridConstraintsAnchorWest(1)
        )

        if (examplesCount != 0) {
            val tabbedPane = JBTabbedPane()
            // tabbedPane.tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT // tabs in one row
            tabbedPane.tabComponentInsets = JBInsets.create(0, 0) // no inner borders for tab content
            tabbedPane.font = io.snyk.plugin.ui.getFont(-1, 14, tabbedPane.font)

            val tabbedPanel = JPanel()
            tabbedPanel.layout = GridLayoutManager(1, 1, JBUI.insetsTop(10), -1, -1)
            tabbedPanel.add(tabbedPane, panelGridConstraints(0))

            this.add(tabbedPanel, panelGridConstraints(2, indent = 1))

            val maxRowCount = fixes.take(examplesCount).maxOfOrNull { it.lines.size } ?: 0
            fixes.take(examplesCount).forEach { exampleCommitFix ->
                val shortURL = exampleCommitFix.commitURL
                    .removePrefix("https://")
                    .replace(Regex("/commit/.*"), "")

                val tabTitle = shortURL.removePrefix("github.com/").let {
                    if (it.length > 50) it.take(50) + "..." else it
                }

                val icon = if (shortURL.startsWith("github.com")) AllIcons.Vcs.Vendors.Github else null

                tabbedPane.addTab(
                    tabTitle,
                    icon,
                    diffPanel(exampleCommitFix, maxRowCount),
                    shortURL
                )
            }
        }
    }

    private fun diffPanel(exampleCommitFix: snyk.common.lsp.ExampleCommitFix, rowCount: Int): JComponent {
        fun shift(colorComponent: Int, d: Double): Int {
            val n = (colorComponent * d).toInt()
            return n.coerceIn(0, 255)
        }

        val baseColor = UIUtil.getTextFieldBackground()
        val addedColor = Color(
            shift(baseColor.red, 0.75),
            baseColor.green,
            shift(baseColor.blue, 0.75)
        )
        val removedColor = Color(
            shift(baseColor.red, 1.25),
            shift(baseColor.green, 0.85),
            shift(baseColor.blue, 0.85)
        )

        val panel = JPanel()
        panel.layout = GridLayoutManager(rowCount, 1, JBUI.emptyInsets(), -1, 0)
        panel.background = baseColor

        exampleCommitFix.lines.forEachIndexed { index, exampleLine ->
            val lineText = "%6d %c %s".format(
                exampleLine.lineNumber,
                when (exampleLine.lineChange) {
                    "added" -> '+'
                    "removed" -> '-'
                    "none" -> ' '
                    else -> '!'
                },
                exampleLine.line
            )
            val codeLine = JTextArea(lineText)

            codeLine.background = when (exampleLine.lineChange) {
                "added" -> addedColor
                "removed" -> removedColor
                "none" -> baseColor
                else -> baseColor
            }
            codeLine.isOpaque = true
            codeLine.isEditable = false
            codeLine.font = io.snyk.plugin.ui.getFont(-1, 14, codeLine.font)

            panel.add(
                codeLine,
                baseGridConstraintsAnchorWest(
                    row = index,
                    fill = GridConstraints.FILL_BOTH,
                    indent = 0
                )
            )
        }

        // fill space with empty lines to avoid rows stretching
        for (i in exampleCommitFix.lines.size..rowCount) {
            val emptyLine = JTextArea("")
            panel.add(
                emptyLine,
                baseGridConstraintsAnchorWest(
                    row = i - 1,
                    fill = GridConstraints.FILL_BOTH,
                    indent = 0
                )
            )
        }

        return ScrollPaneFactory.createScrollPane(
            panel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
    }
}
