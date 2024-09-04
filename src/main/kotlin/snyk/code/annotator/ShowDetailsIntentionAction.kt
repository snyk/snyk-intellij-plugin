package snyk.code.annotator

import io.snyk.plugin.Severity
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.intentionactions.ShowDetailsIntentionActionBase
import snyk.common.lsp.ScanIssue

class ShowDetailsIntentionAction(
    override val annotationMessage: String,
    val issue: ScanIssue
) : ShowDetailsIntentionActionBase() {
    override fun selectNodeAndDisplayDescription(toolWindowPanel: SnykToolWindowPanel) {
        toolWindowPanel.selectNodeAndDisplayDescription(issue)
    }

    override fun getSeverity(): Severity = issue.getSeverityAsEnum()
}
