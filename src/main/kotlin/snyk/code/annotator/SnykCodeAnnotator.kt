package snyk.code.annotator

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.snykcode.getSeverityAsEnum
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.AnnotatorCommon
import snyk.common.intentionactions.ShowDetailsIntentionActionBase

class SnykCodeAnnotator : ExternalAnnotator<PsiFile, Unit>() {
    val logger = logger<SnykCodeAnnotator>()

    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile = file
    override fun doAnnotate(psiFile: PsiFile?) {
        AnnotatorCommon.prepareAnnotate(psiFile)
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        if (isSnykCodeLSEnabled()) return
        val suggestions = getIssuesForFile(psiFile)
            .filter { AnnotatorCommon.isSeverityToShow(it.getSeverityAsEnum()) }

        suggestions.forEach { suggestionForFile ->
            val highlightSeverity = suggestionForFile.getSeverityAsEnum().getHighlightSeverity()
            suggestionForFile.ranges.forEach {
                val textRange = textRange(psiFile, it)
                if (!textRange.isEmpty) {
                    val annotationMessage = annotationMessage(suggestionForFile)
                    holder.newAnnotation(highlightSeverity, "Snyk: $annotationMessage")
                        .range(textRange)
                        .withFix(ShowDetailsIntentionAction(annotationMessage, suggestionForFile, it))
                        .create()
                }
            }
        }
    }

    private fun getIssuesForFile(psiFile: PsiFile): List<SuggestionForFile> =
        AnalysisData.instance.getAnalysis(SnykCodeFile(psiFile.project, psiFile.virtualFile))

    /** Public for Tests only */
    fun annotationMessage(suggestion: SuggestionForFile): String =
        suggestion.title.ifBlank {
            suggestion.message.let {
                if (it.length < 70) it else "${it.take(70)}..."
            }
        }

    /** Public for Tests only */
    fun textRange(psiFile: PsiFile, snykCodeRange: MyTextRange): TextRange {
        try {
            val document =
                psiFile.viewProvider.document ?: throw IllegalArgumentException("No document found for $psiFile")
            val startRow = snykCodeRange.startRow - 1
            val endRow = snykCodeRange.endRow - 1
            val startCol = snykCodeRange.startCol
            val endCol = snykCodeRange.endCol

            if (startRow < 0 || startRow > document.lineCount - 1)
                throw IllegalArgumentException("Invalid range $snykCodeRange")
            if (endRow < 0 || endRow > document.lineCount - 1 || endRow < startRow)
                throw IllegalArgumentException("Invalid range $snykCodeRange")

            val lineOffSet = document.getLineStartOffset(startRow) + startCol
            val lineOffSetEnd = document.getLineStartOffset(endRow) + endCol

            if (lineOffSet < 0 || lineOffSet > document.textLength - 1)
                throw IllegalArgumentException("Invalid range $snykCodeRange")
            if (lineOffSetEnd < 0 || lineOffSetEnd < lineOffSet || lineOffSetEnd > document.textLength - 1)
                throw IllegalArgumentException("Invalid range $snykCodeRange")

            return TextRange.create(lineOffSet, lineOffSetEnd)
        } catch (e: IllegalArgumentException) {
            logger.warn(e)
            return TextRange.EMPTY_RANGE
        }
    }

    inner class ShowDetailsIntentionAction(
        override val annotationMessage: String,
        private val suggestion: SuggestionForFile,
        private val codeTextRange: MyTextRange
    ) : ShowDetailsIntentionActionBase() {

        override fun selectNodeAndDisplayDescription(toolWindowPanel: SnykToolWindowPanel) {
            toolWindowPanel.selectNodeAndDisplayDescription(suggestion, codeTextRange)
        }

        override fun getSeverity(): Severity = suggestion.getSeverityAsEnum()
    }
}
