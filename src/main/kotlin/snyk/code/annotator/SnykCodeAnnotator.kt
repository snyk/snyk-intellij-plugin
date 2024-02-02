package snyk.code.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.AnnotatorCommon
import snyk.common.intentionactions.ShowDetailsIntentionActionBase
import snyk.common.lsp.ScanIssue

class SnykCodeAnnotator : ExternalAnnotator<PsiFile, Unit>() {
    val logger = logger<SnykCodeAnnotator>()

    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile = file
    override fun doAnnotate(psiFile: PsiFile?) = AnnotatorCommon.prepareAnnotate(psiFile)

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        val issues = getIssuesForFile(psiFile)
            .filter { AnnotatorCommon.isSeverityToShow(it.getSeverityAsEnum()) }

        issues.forEach { issue ->
            val highlightSeverity = issue.getSeverityAsEnum().getHighlightSeverity()
            if (issue.textRange?.isEmpty == false) {
                val annotationMessage = annotationMessage(issue)
                holder.newAnnotation(highlightSeverity, "Snyk: $annotationMessage")
                    .range(issue.textRange!!)
                    .withFix(ShowDetailsIntentionAction(annotationMessage, issue))
                    .create()
            }
        }
    }

    private fun getIssuesForFile(psiFile: PsiFile): List<ScanIssue> =
        getSnykCachedResults(psiFile.project)?.currentSnykCodeResults
            ?.filter { it.key.virtualFile == psiFile.virtualFile }
            ?.flatMap { it.value }
            ?: emptyList()

    /** Public for Tests only */
    fun annotationMessage(issue: ScanIssue): String =
        issue.title.ifBlank {
            issue.additionalData.message.let {
                if (it.length < 70) it else "${it.take(70)}..."
            }
        }

    inner class ShowDetailsIntentionAction(
        override val annotationMessage: String,
        private val scanIssue: ScanIssue,
    ) : ShowDetailsIntentionActionBase() {

        override fun selectNodeAndDisplayDescription(toolWindowPanel: SnykToolWindowPanel) {
            toolWindowPanel.selectNodeAndDisplayDescription(scanIssue)
        }

        override fun getSeverity(): Severity = scanIssue.getSeverityAsEnum()
    }
}

