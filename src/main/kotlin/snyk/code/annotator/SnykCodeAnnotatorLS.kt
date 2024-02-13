package snyk.code.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.eclipse.lsp4j.Range
import snyk.common.AnnotatorCommon
import snyk.common.intentionactions.ShowDetailsIntentionActionBase
import snyk.common.lsp.ScanIssue

class SnykCodeAnnotatorLS : ExternalAnnotator<PsiFile, Unit>() {
    val logger = logger<SnykCodeAnnotatorLS>()

    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile = file
    override fun doAnnotate(psiFile: PsiFile?) {
        AnnotatorCommon.prepareAnnotate(psiFile)
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        if (!isSnykCodeLSEnabled()) return
        // FIXME: this is not the LS implementation needed
        getIssuesForFile(psiFile)
            .filter { AnnotatorCommon.isSeverityToShow(it.getSeverityAsEnum()) }
            .forEach {
                val highlightSeverity = it.getSeverityAsEnum().getHighlightSeverity()
                val textRange = textRange(psiFile, it.range)
                if (!textRange.isEmpty) {
                    val annotationMessage = annotationMessage(it)
                    holder.newAnnotation(highlightSeverity, "Snyk: $annotationMessage")
                        .range(textRange)
                        .withFix(ShowDetailsIntentionAction(annotationMessage, it, it.range))
                        .create()
                }
            }
    }

    private fun getIssuesForFile(psiFile: PsiFile): List<ScanIssue> =
        getSnykCachedResults(psiFile.project)?.currentSnykCodeResultsLS
            ?.filter { it.key.virtualFile == psiFile.virtualFile }
            ?.map { it.value }
            ?.flatten()
            ?.toList()
            ?: emptyList()

    /** Public for Tests only */
    fun annotationMessage(issue: ScanIssue): String =
        issue.title.ifBlank {
            issue.additionalData.message.let {
                if (it.length < 70) it else "${it.take(70)}..."
            }
        }.split(":")[0]

    /** Public for Tests only */
    @Suppress("DuplicatedCode")
    fun textRange(psiFile: PsiFile, range: Range): TextRange {
        try {
            val document =
                psiFile.viewProvider.document ?: throw IllegalArgumentException("No document found for $psiFile")
            val startRow = range.start.line
            val endRow = range.end.line
            val startCol = range.start.character
            val endCol = range.end.character

            if (startRow < 0 || startRow > document.lineCount - 1)
                throw IllegalArgumentException("Invalid range $range")
            if (endRow < 0 || endRow > document.lineCount - 1 || endRow < startRow)
                throw IllegalArgumentException("Invalid range $range")

            val lineOffSet = document.getLineStartOffset(startRow) + startCol
            val lineOffSetEnd = document.getLineStartOffset(endRow) + endCol

            if (lineOffSet < 0 || lineOffSet > document.textLength - 1)
                throw IllegalArgumentException("Invalid range $range")
            if (lineOffSetEnd < 0 || lineOffSetEnd < lineOffSet || lineOffSetEnd > document.textLength - 1)
                throw IllegalArgumentException("Invalid range $range")

            return TextRange.create(lineOffSet, lineOffSetEnd)
        } catch (e: IllegalArgumentException) {
            logger.warn(e)
            return TextRange.EMPTY_RANGE
        }
    }

    inner class ShowDetailsIntentionAction(
        override val annotationMessage: String,
        private val issue: ScanIssue,
        private val codeTextRange: Range
    ) : ShowDetailsIntentionActionBase() {

        override fun selectNodeAndDisplayDescription(toolWindowPanel: SnykToolWindowPanel) {
            toolWindowPanel.selectNodeAndDisplayDescription(issue)
        }

        override fun getSeverity(): Severity = issue.getSeverityAsEnum()
    }
}
