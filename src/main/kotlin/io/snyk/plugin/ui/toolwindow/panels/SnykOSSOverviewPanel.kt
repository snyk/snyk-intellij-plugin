package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import io.snyk.plugin.ui.panelGridConstraints
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import snyk.common.lsp.IssueData
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel

class SnykOSSOverviewPanel(private val ossIssueData: IssueData) : JComponent() {
    init {
        name = "overviewPanel"
        this.layout = GridLayoutManager(2, 1, JBUI.insetsLeft(5), -1, 0)

        val descriptionPane = getReadOnlyClickableHtmlJEditorPane(getDescriptionAsHtml())

        this.add(
            descriptionPane,
            panelGridConstraints(1)
        )
    }

    private fun getDescriptionAsHtml(): String {
        val overviewMarkdownStr = ossIssueData.description

        val parser = Parser.builder().build()
        val document = parser.parse(overviewMarkdownStr)

        val renderer = HtmlRenderer.builder().escapeHtml(true).build()

        return renderer.render(document)
    }

}
