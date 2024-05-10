package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.getDocument
import io.snyk.plugin.navigateToSource
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import snyk.common.lsp.DataFlow
import snyk.common.lsp.IssueData
import java.awt.Font
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.event.HyperlinkEvent
import kotlin.math.max

class SnykCodeDataflowPanel(private val project: Project, codeIssueData: IssueData): JPanel() {
    init {
        name = "dataFlowPanel"
        val dataFlow = codeIssueData.dataFlow

        this.layout = GridLayoutManager(1 + dataFlow.size, 1, JBUI.emptyInsets(), -1, 5)

        this.add(
            defaultFontLabel("Data Flow - ${dataFlow.size} step${if (dataFlow.size > 1) "s" else ""}", true),
            baseGridConstraintsAnchorWest(0)
        )

        this.add(
            stepsPanel(dataFlow),
            baseGridConstraintsAnchorWest(1)
        )
    }


    private fun stepsPanel(dataflow: List<DataFlow>): JPanel {
        val panel = JPanel()
        panel.layout = GridLayoutManager(dataflow.size, 1, JBUI.emptyInsets(), 0, 0)
        panel.background = UIUtil.getTextFieldBackground()

        val maxFilenameLength = dataflow.asSequence()
            .filter { it.filePath.isNotEmpty() }
            .map { it.filePath.substringAfterLast('/', "").length }
            .maxOrNull() ?: 0

        val allStepPanels = mutableListOf<JPanel>()
        dataflow.forEach { flow ->
            val stepPanel = stepPanel(
                index = flow.position,
                flow = flow,
                maxFilenameLength = max(flow.filePath.toVirtualFile().name.length, maxFilenameLength),
                allStepPanels = allStepPanels
            )

            panel.add(
                stepPanel,
                baseGridConstraintsAnchorWest(
                    row = flow.position,
                    fill = GridConstraints.FILL_BOTH,
                    indent = 0
                )
            )
            allStepPanels.add(stepPanel)
        }

        return panel
    }

    private fun stepPanel(
        index: Int,
        flow: DataFlow,
        maxFilenameLength: Int,
        allStepPanels: MutableList<JPanel>
    ): JPanel {
        val stepPanel = JPanel()
        stepPanel.layout = GridLayoutManager(1, 3, JBUI.insetsBottom(4), 0, 0)
        stepPanel.background = UIUtil.getTextFieldBackground()

        val paddedStepNumber = (index + 1).toString().padStart(2, ' ')

        val virtualFile = flow.filePath.toVirtualFile()
        val fileName = virtualFile.name

        val lineNumber = flow.flowRange.start.line + 1
        val positionLinkText = "$fileName:$lineNumber".padEnd(maxFilenameLength + 5, ' ')

        val positionLabel = linkLabel(
            beforeLinkText = "$paddedStepNumber  ",
            linkText = positionLinkText,
            afterLinkText = " |",
            toolTipText = "Click to show in the Editor",
            customFont = JTextArea().font
        ) {
            if (!virtualFile.isValid) return@linkLabel

            val document = virtualFile.getDocument()
            val startLineStartOffset = document?.getLineStartOffset(flow.flowRange.start.line) ?: 0
            val startOffset = startLineStartOffset + (flow.flowRange.start.character)
            val endLineStartOffset = document?.getLineStartOffset(flow.flowRange.end.line) ?: 0
            val endOffset = endLineStartOffset + flow.flowRange.end.character - 1

            navigateToSource(this.project, virtualFile, startOffset, endOffset)

            allStepPanels.forEach {
                it.background = UIUtil.getTextFieldBackground()
            }
            stepPanel.background = UIUtil.getTableSelectionBackground(false)
        }
        stepPanel.add(positionLabel, baseGridConstraintsAnchorWest(0, indent = 1))

        val codeLine = codeLine(flow.content.trimStart())
        codeLine.isOpaque = false
        stepPanel.add(
            codeLine,
            baseGridConstraintsAnchorWest(
                row = 0,
                // is needed to avoid center alignment when outer panel is filling horizontal space
                hSizePolicy = GridConstraints.SIZEPOLICY_CAN_GROW,
                column = 1
            )
        )

        return stepPanel
    }

    private fun linkLabel(
        beforeLinkText: String = "",
        linkText: String,
        afterLinkText: String = "",
        toolTipText: String,
        customFont: Font? = null,
        onClick: (HyperlinkEvent) -> Unit
    ): HyperlinkLabel {
        return HyperlinkLabel().apply {
            this.setTextWithHyperlink("$beforeLinkText<hyperlink>$linkText</hyperlink>$afterLinkText")
            this.toolTipText = toolTipText
            this.font = io.snyk.plugin.ui.getFont(-1, 14, customFont ?: font)
            addHyperlinkListener {
                onClick.invoke(it)
            }
        }
    }

    private fun codeLine(content: String): JTextArea {
        val component = JTextArea(content)
        component.font = io.snyk.plugin.ui.getFont(-1, 14, component.font)
        component.isEditable = false
        return component
    }
}
