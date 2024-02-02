package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiFile
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTabbedPane
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.getSnykApiService
import io.snyk.plugin.isReportFalsePositivesEnabled
import io.snyk.plugin.net.FalsePositiveContext
import io.snyk.plugin.net.FalsePositivePayload
import io.snyk.plugin.snykcode.core.PDU
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.ReportFalsePositiveDialog
import io.snyk.plugin.ui.toolwindow.ReportFalsePositiveDialog.Companion.FALSE_POSITIVE_REPORTED_TEXT
import io.snyk.plugin.ui.toolwindow.ReportFalsePositiveDialog.Companion.REPORT_FALSE_POSITIVE_TEXT
import snyk.common.lsp.MarkerPosition
import snyk.common.lsp.ScanIssue
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ScrollPaneConstants
import javax.swing.event.HyperlinkEvent
import kotlin.math.max

class SuggestionDescriptionPanel(
    private val snykCodeFile: SnykCodeFile,
    private val issue: ScanIssue
) : IssueDescriptionPanelBase(title = issue.title, severity = issue.getSeverityAsEnum()) {
    val project = snykCodeFile.project

    init {
        createUI()
    }

    override fun secondRowTitlePanel(): DescriptionHeaderPanel = descriptionHeaderPanel(
        issueNaming = if (issue.additionalData.isSecurityType) "Vulnerability" else "Code Issue",
        cwes = issue.additionalData.cwe ?: emptyList()
    )

    override fun createMainBodyPanel(): Pair<JPanel, Int> {
        val lastRowToAddSpacer = 5
        val panel = JPanel(
            GridLayoutManager(lastRowToAddSpacer + 1, 1, Insets(0, 10, 20, 10), -1, 20)
        ).apply {
            this.add(overviewPanel(), panelGridConstraints(2))

            dataFlowPanel()?.let { this.add(it, panelGridConstraints(3)) }

            this.add(fixExamplesPanel(), panelGridConstraints(4))
        }
        return Pair(panel, lastRowToAddSpacer)
    }

    private fun overviewPanel(): JComponent {
        val panel = JPanel()
        panel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, -1)
        val label = JLabel("<html>" + getOverviewText() + "</html>").apply {
            this.isOpaque = false
            this.background = UIUtil.getPanelBackground()
            this.font = io.snyk.plugin.ui.getFont(-1, 14, panel.font)
            this.preferredSize = Dimension() // this is the key part for shrink/grow.
        }
        panel.add(label, panelGridConstraints(1, indent = 1))

        return panel
    }

    private fun codeLine(range: MarkerPosition, file: SnykCodeFile?): JTextArea {
        val component = JTextArea(getLineOfCode(range, file))
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

    private fun getLineOfCode(range: MarkerPosition, file: SnykCodeFile?): String {
        return "to be added"
    }

    private fun dataFlowPanel(): JPanel? {
        // FIXME
//        val markers = issue.additionalData.markers?.let { marker ->
//            marker.map { it.pos }.flatten().distinctBy { it.file + it.position.rows?.firstOrNull() }
//        } ?: emptyList()
//
//        if (markers.isEmpty()) return null
//
//        val panel = JPanel()
//        panel.layout = GridLayoutManager(1 + markers.size, 1, Insets(0, 0, 0, 0), -1, 5)
//
//        panel.add(
//            defaultFontLabel("Data Flow - ${markers.size} step${if (markers.size > 1) "s" else ""}", true),
//            baseGridConstraintsAnchorWest(0)
//        )
//
//        panel.add(
//            stepsPanel(markers),
//            baseGridConstraintsAnchorWest(1)
//        )
//
//        return panel
        return null
    }

    private fun stepsPanel(markers: List<MarkerPosition>): JPanel {
        val panel = JPanel()
        panel.layout = GridLayoutManager(markers.size, 1, Insets(0, 0, 0, 0), 0, 0)
        panel.background = UIUtil.getTextFieldBackground()

        val maxFilenameLength = markers.asSequence()
            .filter { it.file.isNotEmpty() }
            .map { it.file.substringAfterLast('/', "").length }
            .maxOrNull() ?: 0

        val allStepPanels = mutableListOf<JPanel>()
        markers.forEachIndexed { index, markerRange ->
            val stepPanel = stepPanel(
                index = index,
                markerRange = markerRange,
                maxFilenameLength = max(snykCodeFile.virtualFile.name.length, maxFilenameLength),
                allStepPanels = allStepPanels
            )

            panel.add(
                stepPanel,
                baseGridConstraintsAnchorWest(
                    row = index,
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
        markerRange: MarkerPosition,
        maxFilenameLength: Int,
        allStepPanels: MutableList<JPanel>
    ): JPanel {
        val stepPanel = JPanel()
        stepPanel.layout = GridLayoutManager(1, 3, Insets(0, 0, 4, 0), 0, 0)
        stepPanel.background = UIUtil.getTextFieldBackground()

        val paddedStepNumber = (index + 1).toString().padStart(2, ' ')

        val fileName = snykCodeFile.virtualFile.name

        val positionLinkText = "$fileName:${markerRange.position.rows?.get(0)}".padEnd(maxFilenameLength + 5, ' ')

        val positionLabel = linkLabel(
            beforeLinkText = "$paddedStepNumber  ",
            linkText = positionLinkText,
            afterLinkText = " |",
            toolTipText = "Click to show in the Editor",
            customFont = JTextArea().font
        ) {
            if (!snykCodeFile.virtualFile.isValid) return@linkLabel

//            navigateToSource(project, snykCodeFile.virtualFile, markerRange.startOffset, markerRange.endOffset)

            allStepPanels.forEach {
                it.background = UIUtil.getTextFieldBackground()
            }
            stepPanel.background = UIUtil.getTableSelectionBackground(false)
        }
        stepPanel.add(positionLabel, baseGridConstraintsAnchorWest(0, indent = 1))

        val codeLine = codeLine(markerRange, snykCodeFile)
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

    private fun fixExamplesPanel(): JPanel {
        val fixes = issue.additionalData.exampleCommitFixes
        val examplesCount = fixes.size.coerceAtMost(3)

        val panel = JPanel()
        panel.layout = GridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, 5)

        panel.add(
            defaultFontLabel("External example fixes", true),
            baseGridConstraintsAnchorWest(0)
        )

        val labelText =
            if (examplesCount == 0) "No example fixes available."
            else "This issue was fixed by ${issue.additionalData.repoDatasetSize} projects. Here are $examplesCount example fixes."
        panel.add(
            defaultFontLabel(labelText),
            baseGridConstraintsAnchorWest(1)
        )

        if (examplesCount == 0) return panel

        val tabbedPane = JBTabbedPane()
        //tabbedPane.tabLayoutPolicy = JTabbedPane.SCROLL_TAB_LAYOUT // tabs in one row
        tabbedPane.tabComponentInsets = JBInsets.create(0, 0) // no inner borders for tab content
        tabbedPane.font = io.snyk.plugin.ui.getFont(-1, 14, tabbedPane.font)

        val tabbedPanel = JPanel()
        tabbedPanel.layout = GridLayoutManager(1, 1, Insets(10, 0, 0, 0), -1, -1)
        tabbedPanel.add(tabbedPane, panelGridConstraints(0))

        panel.add(tabbedPanel, panelGridConstraints(2, indent = 1))

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

        return panel
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

    private fun getOverviewText(): String {
        return issue.additionalData.message
    }

    override fun getBottomRightButtons(): List<JButton> = mutableListOf<JButton>()
        .apply {
            if (isReportFalsePositivesEnabled()) add(reportFalsePositiveButton)
        }
        .toList()

    private val reportFalsePositiveButton: JButton
        get() = JButton(REPORT_FALSE_POSITIVE_TEXT).apply {
            addActionListener {
                val dialog = ReportFalsePositiveDialog(
                    project,
                    titlePanel(insets = JBUI.insetsBottom(10), indent = 0),
                    getRelatedPsiFiles()
                )
                if (dialog.showAndGet()) {
                    val payload = FalsePositivePayload(
                        topic = "False Positive",
                        message = issue.additionalData.message,
                        context = FalsePositiveContext(
                            issueId = issue.id,
                            userPublicId = getSnykApiService().userId ?: "",
                            startLine = issue.range.start?.line ?: 1,
                            endLine = issue.range.end?.line ?: 1,
                            primaryFilePath = snykCodeFile.virtualFile.path,
                            vulnName = issue.additionalData.rule,
                            fileContents = dialog.result
                        )
                    )
                    if (getSnykApiService().reportFalsePositive(payload)) {
                        this.isEnabled = false
                        this.text = FALSE_POSITIVE_REPORTED_TEXT
                        SnykBalloonNotificationHelper.showInfo(
                            "False Positive reported. Thank you!",
                            project
                        )
                        // todo(?): disable re-report per session/project/application/token/org
                    } else {
                        SnykBalloonNotificationHelper.showError(
                            "Unable to send report, please contact support@snyk.io for assistance",
                            project
                        )
                    }
                }
            }
        }

    private fun getRelatedPsiFiles(): Set<PsiFile> {
        val markersWithUniqFiles = issue.additionalData.markers?.flatMap { m ->
            m.pos.distinctBy { it.file }
        }
        val filesFromMarkers: Set<SnykCodeFile> = markersWithUniqFiles
            ?.mapNotNull { PDU.instance.getFileByDeepcodedPath(it.file, project) }
            ?.map { PDU.toSnykCodeFile(it) }
            ?.toSet()
            ?: emptySet()
        return (setOf(snykCodeFile) + filesFromMarkers)
            .mapNotNull { PDU.toPsiFile(it) }
            .toSet()
    }
}
