package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import ai.deepcode.javaclient.responses.ExampleCommitFix
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import io.snyk.plugin.snykcode.severityAsString
import io.snyk.plugin.ui.buildBoldTitleLabel
import java.awt.*
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import kotlin.math.min

class SuggestionDescriptionPanel(
    private val psiFile: PsiFile,
    private val suggestion: SuggestionForFile
) : JPanel() {

    private fun getGridConstraints(
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

    private fun getPanelGridConstraints(row: Int, indent: Int = 0): GridConstraints {
        return getGridConstraints(
            row,
            anchor = GridConstraints.ANCHOR_CENTER,
            fill = GridConstraints.FILL_BOTH,
            HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
            VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
            indent = indent
        )
    }

    init {
        this.layout = GridLayoutManager(6, 1, Insets(20, 10, 20, 10), -1, 20)

        this.add(
            Spacer(),
            getGridConstraints(
                5,
                anchor = GridConstraints.ANCHOR_CENTER,
                fill = GridConstraints.FILL_VERTICAL,
                HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
                VSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        this.add(overviewPanel(), getPanelGridConstraints(2))

        dataFlowPanel()?.let { this.add(it, getPanelGridConstraints(3)) }

        this.add(fixExamplesPanel(), getPanelGridConstraints(4))

        this.add(titlePanel(), getPanelGridConstraints(0))
    }

    private fun titlePanel(): JPanel {
        val titlePanel = JPanel()
        titlePanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, 5)

        val titleLabel = JLabel().apply {
            font = io.snyk.plugin.ui.getFont(Font.BOLD, 20, font)
            text = " " + if (suggestion.title.isNotBlank()) suggestion.title else when (suggestion.severity) {
                3 -> "High Severity"
                2 -> "Medium Severity"
                1 -> "Low Severity"
                else -> ""
            }
            icon = SnykIcons.getSeverityIcon(suggestion.severityAsString, SnykIcons.IconSize.SIZE24)
        }

        titlePanel.add(
            titleLabel,
            getGridConstraints(0)
        )

        titlePanel.add(cwePanel(), getGridConstraints(1, indent = 0))

        return titlePanel
    }

    private fun overviewPanel(): JPanel {
        val overviewPanel = JPanel()
        overviewPanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, -1)

        val descriptionTextArea = JTextArea(getOverviewText()).apply {
            this.lineWrap = true
            this.wrapStyleWord = true
            this.isOpaque = false
            this.isEditable = false
            this.background = UIUtil.getPanelBackground()
            this.font = io.snyk.plugin.ui.getFont(-1, 16, overviewPanel.font)
        }

        overviewPanel.add(
            ScrollPaneFactory.createScrollPane(descriptionTextArea, true),
            getGridConstraints(
                1,
                anchor = GridConstraints.ANCHOR_CENTER,
                fill = GridConstraints.FILL_BOTH,
                HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
                VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW
            )
        )

        return overviewPanel
    }

    private fun codeLine(range: MyTextRange, prefix: String): JTextArea {
        val component = JTextArea(prefix + getLineOfCode(range))
        component.font = io.snyk.plugin.ui.getFont(-1, 14, component.font)
        component.isEditable = false
        return component
    }

    private fun defaultFontLabel(labelText: String, bold: Boolean = false): JLabel {
        return JLabel().apply {
            val titleLabelFont: Font? = io.snyk.plugin.ui.getFont(if (bold) Font.BOLD else -1, 14, font)
            titleLabelFont?.let { font = it }
            text = labelText
        }
    }

    private fun linkLabel(
        beforeLinkText: String = "",
        linkText: String,
        afterLinkText: String = "",
        toolTipText: String,
        customFont: Font? = null,
        onClick: (HyperlinkEvent) -> Unit): HyperlinkLabel {
        return HyperlinkLabel().apply {
            this.setHyperlinkText(beforeLinkText, linkText, afterLinkText)
            this.toolTipText = toolTipText
            this.font = io.snyk.plugin.ui.getFont(-1, 14, customFont ?: font)
            addHyperlinkListener {
                onClick.invoke(it)
            }
        }
    }

    private fun getLineOfCode(range: MyTextRange): String {
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
            ?: throw IllegalStateException("No document found for $psiFile")
        val chars = document.charsSequence
        val startOffset = range.start
        val endOffset = range.end
        val lineNumber = document.getLineNumber(startOffset)
        var lineStartOffset = document.getLineStartOffset(lineNumber)
        val columnNumber = startOffset - lineStartOffset

        // skip all white space characters
        while (lineStartOffset < document.textLength
            && (chars[lineStartOffset] == '\t' || chars[lineStartOffset] == ' ')
        ) {
            lineStartOffset++
        }
        val lineEndOffset = document.getLineEndOffset(lineNumber)

        val lineColumnPrefix = ""//"(${lineNumber + 1}, ${columnNumber + 1}) "
        val codeInLine = chars.subSequence(lineStartOffset, min(lineEndOffset, chars.length)).toString()

        return lineColumnPrefix + codeInLine
    }

    private fun dataFlowPanel(): JPanel? {
        val suggestionRange = suggestion.ranges.firstOrNull()
        val markers = suggestionRange?.let { range ->
            range.markers.values.flatten().distinctBy { it.startRow }
        } ?: emptyList()

        if (markers.isEmpty()) return null

        val panel = JPanel()
        panel.layout = GridLayoutManager(1 + markers.size, 1, Insets(0, 0, 0, 0), -1, 5)

        panel.add(
            buildBoldTitleLabel("Data Flow - ${markers.size} step${if (markers.size > 1) "s" else ""}"),
            getGridConstraints(0)
        )

        panel.add(
            stepsPanel(markers),
            getGridConstraints(1)
        )

        return panel
    }

    private fun stepsPanel(markers: List<MyTextRange>): JPanel {
        val panel = JPanel()
        panel.layout = GridLayoutManager(markers.size, 1, Insets(0, 0, 0, 0), 0, 0)
        panel.background = UIUtil.getTextFieldBackground()

        val allStepPanels = mutableListOf<JPanel>()
        markers.forEachIndexed { index, markerRange ->
            val stepPanel = stepPanel(index, markerRange, allStepPanels)
            panel.add(
                stepPanel,
                getGridConstraints(
                    row = index,
                    fill = GridConstraints.FILL_BOTH,
                    indent = 0
                )
            )
            allStepPanels.add(stepPanel)
        }

        return panel
    }

    private fun stepPanel(index: Int, markerRange: MyTextRange, allStepPanels: MutableList<JPanel>): JPanel {
        val stepPanel = JPanel()
        stepPanel.layout = GridLayoutManager(1, 3, Insets(0, 0, 4, 0), 0, 0)
        stepPanel.background = UIUtil.getTextFieldBackground()

        val paddedStepNumber = (index + 1).toString().padStart(2, ' ')
        val paddedRowNumber = markerRange.startRow.toString().padStart(4, ' ')
        val positionLinkText = "${psiFile.name}:$paddedRowNumber"

        val positionLabel = linkLabel(
            beforeLinkText = "$paddedStepNumber  ",
            linkText = positionLinkText,
            afterLinkText = "  |",
            toolTipText = "Click to show in the Editor",
            customFont = JTextArea().font
        ) {
            if (!psiFile.virtualFile.isValid) return@linkLabel
            // jump to Source
            PsiNavigationSupport.getInstance().createNavigatable(
                psiFile.project,
                psiFile.virtualFile,
                markerRange.start
            ).navigate(false)

            // highlight(by selection) marker range in source file
            val editor = FileEditorManager.getInstance(psiFile.project).selectedTextEditor
            editor?.selectionModel?.setSelection(markerRange.start, markerRange.end)

            allStepPanels.forEach {
                it.background = UIUtil.getTextFieldBackground()
            }
            stepPanel.background = UIUtil.getTableSelectionBackground(false)
        }
        stepPanel.add(positionLabel, getGridConstraints(0, indent = 1))

        val codeLine = codeLine(markerRange, "")
        codeLine.isOpaque = false
        stepPanel.add(
            codeLine,
            getGridConstraints(
                row = 0,
                // is needed to avoid center alignment when outer panel is filling horizontal space
                HSizePolicy = GridConstraints.SIZEPOLICY_CAN_GROW,
                column = 1
            )
        )

        return stepPanel
    }

    private fun fixExamplesPanel(): JPanel {
        val fixes = suggestion.exampleCommitFixes
        val examplesCount = fixes.size.coerceAtMost(3)

        val panel = JPanel()
        panel.layout = GridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, 5)

        panel.add(
            buildBoldTitleLabel("External example fixes"),
            getGridConstraints(0)
        )

        val labelText =
            if (examplesCount == 0) "No example fixes available."
            else "This issue was fixed by ${suggestion.repoDatasetSize} projects. Here are $examplesCount example fixes."
        panel.add(
            defaultFontLabel(labelText),
            getGridConstraints(1)
        )

        if (examplesCount == 0) return panel

        val tabbedPane = JBTabbedPane()
        //tabbedPane.tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT // tabs in one row
        tabbedPane.tabComponentInsets = JBInsets.create(0, 0) // no inner borders for tab content
        tabbedPane.font = io.snyk.plugin.ui.getFont(-1, 14, tabbedPane.font)

        val tabbedPanel = JPanel()
        tabbedPanel.layout = GridLayoutManager(1, 1, Insets(10, 0, 0, 0), -1, -1)
        tabbedPanel.add(tabbedPane, getPanelGridConstraints(0))

        panel.add(tabbedPanel, getPanelGridConstraints(2, indent = 1))

        val maxRowCount = fixes.take(examplesCount)
            .map { it.lines.size }
            .max()
            ?: 0
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

        return panel
    }

    private fun diffPanel(exampleCommitFix: ExampleCommitFix, rowCount: Int): JComponent {

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
        panel.layout = GridLayoutManager(rowCount, 1, Insets(0, 0, 0, 0), -1, 0)
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
                getGridConstraints(
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
                getGridConstraints(
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


    private fun cwePanel(): Component {
        val panel = JPanel()
        val cwes = suggestion.cwe

        panel.layout = GridLayoutManager(1, 1 + cwes.size, Insets(0, 0, 0, 0), 5, 0)

        panel.add(
            defaultFontLabel(
                if (suggestion.categories.contains("Security")) "Vulnerability  |" else "Code Issue"
            ),
            getGridConstraints(0)
        )

        cwes.forEachIndexed() { index, cwe ->
            if (!cwe.isNullOrEmpty()) {
                val positionLabel = linkLabel(
                    linkText = cwe,
                    toolTipText = "Click to open description in the Browser"
                ) {
                    val url = "https://cwe.mitre.org/data/definitions/${cwe.removePrefix("CWE-")}.html"
                    BrowserUtil.open(url)
                }

                panel.add(positionLabel, getGridConstraints(0, column = index + 1, indent = 0))
            }
        }

        return panel
    }

    private fun getOverviewText(): String {
        return suggestion.message
    }
}
