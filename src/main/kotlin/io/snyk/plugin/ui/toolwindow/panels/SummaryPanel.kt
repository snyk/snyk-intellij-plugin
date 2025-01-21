package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.addAndGetCenteredPanel
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.getStandardLayout
import javax.swing.JEditorPane
import javax.swing.JPanel

class SummaryPanel() : JPanel(), Disposable {

    init {
        name = "summaryPanel"

        layout = getStandardLayout(1, 1)
        val panel = addAndGetCenteredPanel(this, 1, 1)
        val htmlContent = JEditorPane("text/html", getInitialText())
        htmlContent.isEditable = false

        panel.add(
            htmlContent,
            baseGridConstraints(row = 0, column = 0, anchor = ANCHOR_WEST)
        )

    }

    private fun getInitialText(): String {
        return """
        |<html>
        |  Summary placeholder
        |</html>
        """.trimMargin()
    }

    private fun allIssuesClicked() {
        pluginSettings().setDeltaDisabled()
        // TODO - Get new HTML from LS
    }

    private fun newIssuesClicked() {
        pluginSettings().setDeltaEnabled()
        // TODO - Get new HTML from LS
    }

    override fun dispose() {}

}
