package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import ai.deepcode.javaclient.responses.ExampleCommitFix
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.snykcode.severityAsString
import io.snyk.plugin.ui.buildBoldTitleLabel
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
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

    private fun getPanelGridConstraints(row: Int): GridConstraints {
        return getGridConstraints(
            row,
            anchor = GridConstraints.ANCHOR_CENTER,
            fill = GridConstraints.FILL_BOTH,
            HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
            VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
            indent = 0
        )
    }

    init {
        this.layout = GridLayoutManager(6, 1, Insets(20, 0, 0, 0), -1, 10)

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

//        val codeRange = suggestion.ranges.firstOrNull()
//        codeRange?.let {
//            this.add(codeLine(it, "(${it.startRow}:${it.startCol})  "), getGridConstraints(1))
//        }

        this.add(overviewPanel(), getPanelGridConstraints(2))

        this.add(dataFlowPanel(), getPanelGridConstraints(3))

        this.add(fixExamplesPanel(), getPanelGridConstraints(4))

        this.add(severityPanel(), getPanelGridConstraints(0))
    }

    private fun severityPanel(): SeverityColorPanel {
        val severityPanel = SeverityColorPanel(suggestion.severityAsString)
        severityPanel.layout = GridLayoutManager(2, 2, Insets(10, 10, 10, 10), -1, -1)

        val severityLabel = JLabel()

        val severityLabelFont: Font? = io.snyk.plugin.ui.getFont(-1, 14, severityLabel.font)

        if (severityLabelFont != null) {
            severityLabel.font = severityLabelFont
        }

        severityLabel.text = when (suggestion.severityAsString) {
            "high" -> "HIGH SEVERITY"
            "medium" -> "MEDIUM SEVERITY"
            "low" -> "LOW SEVERITY"
            else -> "UNKNOWN SEVERITY"
        }

        severityLabel.foreground = Color(-1)

        severityPanel.add(
            severityLabel,
            getGridConstraints(0, indent = 0)
        )

        severityPanel.add(
            Spacer(),
            getGridConstraints(
                0,
                anchor = GridConstraints.ANCHOR_CENTER,
                fill = GridConstraints.FILL_HORIZONTAL,
                HSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW,
                VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0
            )
        )

        severityPanel.add(
            Spacer(),
            getGridConstraints(
                0,
                anchor = GridConstraints.ANCHOR_CENTER,
                fill = GridConstraints.FILL_VERTICAL,
                HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
                VSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        return severityPanel
    }

    private fun overviewPanel(): JPanel {
        val overviewPanel = JPanel()
        overviewPanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, -1)

        overviewPanel.add(
            buildBoldTitleLabel("Overview"),
            getGridConstraints(0)
        )

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
                VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 2
            )
        )

        return overviewPanel
    }

    private fun codeLine(range: MyTextRange, prefix: String): Component {
        val component = JTextArea(prefix + getLineOfCode(range))
        component.font = io.snyk.plugin.ui.getFont(-1, 14, component.font)
        component.isEditable = false
        return component
    }

    private fun defaultFontLabel(labelText: String): JLabel {
        return JLabel().apply {
            val titleLabelFont: Font? = io.snyk.plugin.ui.getFont(-1, 14, font)
            titleLabelFont?.let { font = it }
            text = labelText
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

    private fun dataFlowPanel(): JPanel {
        val panel = JPanel()
        val suggestionRange = suggestion.ranges.firstOrNull()
        val markers = suggestionRange?.let { range ->
            range.markers.values.flatten().distinctBy { it.startRow }
        } ?: emptyList()

        panel.layout = GridLayoutManager(1 + markers.size, 1, Insets(0, 0, 0, 0), -1, -1)

        panel.add(
            buildBoldTitleLabel("Data Flow"),
            getGridConstraints(0)
        )

        markers.forEachIndexed { index, markerRange ->
            val prefix = "${index + 1}  ${psiFile.name}:${markerRange.startRow}  | "
            val positionLabel = defaultFontLabel(prefix)

            positionLabel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                    // jump to Source
                    PsiNavigationSupport.getInstance().createNavigatable(
                        psiFile.project,
                        psiFile.virtualFile,
                        markerRange.start
                    ).navigate(false)

                    // highlight(by selection) marker range in source file
                    val editor = FileEditorManager.getInstance(psiFile.project).selectedTextEditor
                    editor?.selectionModel?.setSelection(markerRange.start, markerRange.end)
                }

                override fun mouseEntered(e: MouseEvent?) {
                    positionLabel.foreground = JBUI.CurrentTheme.Link.linkColor()
                }

                override fun mouseExited(e: MouseEvent?) {
                    positionLabel.foreground = UIUtil.getLabelForeground()
                }
            })

            panel.add(positionLabel, getGridConstraints(1 + index, indent = 2))

            panel.add(
                codeLine(markerRange, ""),
                getGridConstraints(1 + index, indent = prefix.length) // fixme
            )
        }

        return panel
    }

    private fun fixExamplesPanel(): JPanel {
        val panel = JPanel()
        panel.layout = GridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, -1)

        panel.add(
            buildBoldTitleLabel("External examples fixes"),
            getGridConstraints(0)
        )

        val fixes = suggestion.exampleCommitFixes
        val examplesCount = fixes.size.coerceAtMost(3)

        panel.add(
            defaultFontLabel(
                "This issue was fixed by ${suggestion.repoDatasetSize} projects." +
                    if (examplesCount > 0) " Here are $examplesCount example fixes." else ""
            ),
            getGridConstraints(1, indent = 2)
        )

        val tabbedPane = JBTabbedPane()
        //tabbedPane.tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT // tabs in one row
        tabbedPane.tabComponentInsets = JBInsets.create(0, 0) // no inner borders for tab content

        panel.add(tabbedPane, getPanelGridConstraints(2))

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
                diffPanel(exampleCommitFix),
                shortURL
            )
        }

        return panel
    }

    private fun diffPanel(exampleCommitFix: ExampleCommitFix): JComponent {

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
        panel.layout = GridLayoutManager(exampleCommitFix.lines.size, 1, Insets(0, 0, 0, 0), -1, 0)
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
                codeLine, getGridConstraints(
                    row = index,
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

    private fun getOverviewText(): String {
        return suggestion.message
    }
}
