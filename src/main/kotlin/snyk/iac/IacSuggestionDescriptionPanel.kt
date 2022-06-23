package snyk.iac

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.boldLabel
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.getFont
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import io.snyk.plugin.ui.insertTitleAndResizableTextIntoPanelColumns
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import io.snyk.plugin.ui.toolwindow.LabelProvider
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import snyk.common.IgnoreService
import java.awt.Insets
import java.net.MalformedURLException
import java.net.URL
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

class IacSuggestionDescriptionPanel(
    val issue: IacIssue,
    val psiFile: PsiFile?,
    val project: Project
) : IssueDescriptionPanelBase(title = issue.title, severity = issue.getSeverity()) {

    private val labelProvider = LabelProvider()

    init {
        this.name = "IacSuggestionDescriptionPanel"
        createUI()
    }

    override fun secondRowTitlePanel(): DescriptionHeaderPanel = descriptionHeaderPanel(
        issueNaming = "Issue",
        id = issue.id,
        idUrl = issue.documentation
    )

    override fun createMainBodyPanel(): Pair<JPanel, Int> {
        val panel = JPanel()
        val lastRowToAddSpacer = 9
        panel.layout = GridLayoutManager(lastRowToAddSpacer + 1, 1, Insets(0, 10, 20, 20), -1, 10)

        panel.add(
            descriptionImpactPathPanel(), panelGridConstraints(1)
        )

        if (!issue.resolve.isNullOrBlank()) {
            panel.add(
                remediationPanelWithTitle(issue.resolve), panelGridConstraints(6)
            )
        }

        val referencePanel = addIssueReferences()
        if (referencePanel != null) {
            panel.add(referencePanel, panelGridConstraints(row = 7))
        }

        return Pair(panel, lastRowToAddSpacer)
    }

    private fun descriptionImpactPathPanel(): JPanel {
        val mainBodyPanel = JPanel()
        mainBodyPanel.layout = GridLayoutManager(11, 2, Insets(10, 0, 20, 0), 50, -1)

        insertTitleAndResizableTextIntoPanelColumns(
            panel = mainBodyPanel,
            row = 0,
            title = "Description",
            htmlText = issue.issue
        )

        insertTitleAndResizableTextIntoPanelColumns(
            panel = mainBodyPanel,
            row = 1,
            title = "Impact",
            htmlText = issue.impact
        )

        insertTitleAndResizableTextIntoPanelColumns(
            panel = mainBodyPanel,
            row = 2,
            title = "Path",
            htmlText = issue.path.joinToString(" > "),
            textFont = getFont(-1, -1, JTextArea().font) ?: UIUtil.getLabelFont()
        )

        return mainBodyPanel
    }

    private fun remediationPanel(resolve: String): JPanel {
        val remediationPanel = JPanel()
        remediationPanel.layout = GridLayoutManager(2, 1, Insets(0, 10, 20, 0), -1, -1)
        remediationPanel.background = UIUtil.getTextFieldBackground()

        val resolveMarkdown = markdownToHtml(resolve)
        val whiteBox = getReadOnlyClickableHtmlJEditorPane(
            resolveMarkdown
        ).apply {
            isOpaque = false
        }

        remediationPanel.add(whiteBox, panelGridConstraints(row = 1))

        return remediationPanel
    }

    private fun addIssueReferences(): JPanel? {
        if (issue.references.isNotEmpty()) {
            val panel = JPanel()
            panel.layout = GridLayoutManager(
                issue.references.size + 2, 1, Insets(20, 0, 0, 0), 50, -1
            )

            panel.add(boldLabel("References"), baseGridConstraintsAnchorWest(row = 1))
            issue.references.forEachIndexed { index, s ->
                val label = try {
                    labelProvider.createLinkLabel(URL(s), s)
                } catch (e: MalformedURLException) {
                    JLabel(s)
                }
                panel.add(label, baseGridConstraintsAnchorWest(2 + index))
            }
            return panel
        }
        return null
    }

    private fun markdownToHtml(sourceStr: String): String {
        val parser = Parser.builder().build()
        val document = parser.parse(sourceStr)

        val renderer = HtmlRenderer.builder().escapeHtml(true).build()

        return renderer.render(document)
    }

    private fun remediationPanelWithTitle(remediation: String): JPanel {
        val remediationPanel = JPanel()
        remediationPanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), 50, -1)

        remediationPanel.add(
            boldLabel("Remediation"),
            baseGridConstraintsAnchorWest(row = 0)
        )

        remediationPanel.add(
            remediationPanel(remediation),
            panelGridConstraints(row = 1, indent = 1)
        )

        return remediationPanel
    }

    override fun getBottomRightButtons(): List<JButton> = listOf(
        JButton().apply {
            if (issue.ignored) {
                text = IgnoreButtonActionListener.IGNORED_ISSUE_BUTTON_TEXT
                isEnabled = false
            } else {
                text = "Ignore This Issue"
                addActionListener(IgnoreButtonActionListener(IgnoreService(project), issue, psiFile, project))
            }
            name = "ignoreButton"
        }
    )

}
