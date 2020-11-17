package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.snykcode.severityAsString
import io.snyk.plugin.ui.buildBoldTitleLabel
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.math.min

class SuggestionDescriptionPanel(
    private val psiFile: PsiFile,
    private val suggestion: SuggestionForFile
) : JPanel() {

    init {
        this.layout = GridLayoutManager(11, 1, Insets(20, 0, 0, 0), -1, 10)

        this.add(Spacer(),
            GridConstraints(
                10,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                1,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0, false))

        this.add(severityPanel(),
            GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false))

        this.add(overviewPanel(),
            GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
                GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                1,
                false))

        suggestion.ranges.forEachIndexed { index, range ->
            this.add(codeLineLabel(range), getCodeLineGridConstraints(index))
        }

    }

    private fun getCodeLineGridConstraints(index: Int): GridConstraints {
        return GridConstraints(
            2 + index,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            1,
            false)
    }

    private fun codeLineLabel(range: MyTextRange): JLabel {
        return JLabel().apply {
            val titleLabelFont: Font? = io.snyk.plugin.ui.getFont(-1, 14, font)
            titleLabelFont?.let { font = it }
            text = getLineOfCode(range)
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
            && (chars[lineStartOffset] == '\t' || chars[lineStartOffset] == ' ')) {
            lineStartOffset++
        }
        val lineEndOffset = document.getLineEndOffset(lineNumber)

        val lineColumnPrefix = "(${lineNumber + 1}, ${columnNumber + 1}) "
        val codeInLine = chars.subSequence(lineStartOffset, min(lineEndOffset, chars.length)).toString()

        return lineColumnPrefix + codeInLine
    }

    private fun overviewPanel(): JPanel {
        val overviewPanel = JPanel()
        overviewPanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, -1)

        overviewPanel.add(buildBoldTitleLabel("Overview"),
            GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        val descriptionTextArea = JTextArea(getOverviewText()).apply {
            this.lineWrap = true
            this.wrapStyleWord = true
            this.isOpaque = false
            this.isEditable = false
            this.background = UIUtil.getPanelBackground()
            this.font = io.snyk.plugin.ui.getFont(-1, 16, this.font)
        }

        overviewPanel.add(ScrollPaneFactory.createScrollPane(descriptionTextArea, true),
            GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                null,
                null,
                null,
                1,
                false))

        return overviewPanel
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

        severityPanel.add(severityLabel,
            GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        severityPanel.add(Spacer(),
            GridConstraints(
                0,
                1,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false))

        severityPanel.add(Spacer(),
            GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                1,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false))

        return severityPanel
    }

    private fun getOverviewText(): String {
        return suggestion.message
//        val overviewMarkdownStr = suggestion.getOverview()
//
//        val parser = Parser.builder().build()
//        val document = parser.parse(overviewMarkdownStr)
//
//        val stringBuilder = StringBuilder()
//        val renderer = TextContentRenderer.builder().build()
//
//        renderer.render(document, stringBuilder)
//
//        return stringBuilder.toString()
    }
}
