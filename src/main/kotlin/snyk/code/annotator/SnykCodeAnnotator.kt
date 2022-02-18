package snyk.code.annotator

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.snykcode.core.AnalysisData
import snyk.common.AnnotatorCommon
import kotlin.math.max
import kotlin.math.min

class SnykCodeAnnotator : ExternalAnnotator<PsiFile, Unit>() {
    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile = file
    override fun doAnnotate(psiFile: PsiFile?) {
        AnnotatorCommon.prepareAnnotate(psiFile)
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        val suggestions = getIssuesForFile(psiFile)

        suggestions.forEach { suggestionForFile ->
            val severity = severity(suggestionForFile)
            suggestionForFile.ranges.forEach {
                val textRange = textRange(psiFile, it)
                if (!textRange.isEmpty) {
                    val annotationBuilder =
                        holder.newAnnotation(severity, annotationMessage(suggestionForFile)).range(textRange)
                    annotationBuilder.create()
                }
            }
        }
    }

    fun severity(suggestion: SuggestionForFile): HighlightSeverity {
        return when (suggestion.severity) {
            3 -> HighlightSeverity.ERROR
            2 -> HighlightSeverity.WARNING
            1 -> HighlightSeverity.WEAK_WARNING
            else -> HighlightSeverity.INFORMATION
        }
    }

    private fun getIssuesForFile(psiFile: PsiFile): List<SuggestionForFile> {
        ProgressManager.checkCanceled()
        return AnalysisData.instance.getAnalysis(psiFile)
    }

    fun annotationMessage(suggestion: SuggestionForFile): String {
        return buildString {
            val title = if (suggestion.title.isNotBlank()) " " + suggestion.title + "." else ""
            append("Snyk Code:$title ${suggestion.message}")
        }
    }

    fun textRange(psiFile: PsiFile, snykCodeRange: MyTextRange): TextRange {
        val document = psiFile.viewProvider.document ?: return TextRange.EMPTY_RANGE
        // ensure we stay within doc boundaries
        // Snyk Code is 1-based, Document is 0-based indexes
        val startRow = max(0, snykCodeRange.startRow - 1)
        val endRow = max(startRow, min(document.lineCount - 1, snykCodeRange.endRow - 1))
        val startCol = max(0, snykCodeRange.startCol)
        val endCol = max(startCol, min(document.getLineEndOffset(endRow), snykCodeRange.endCol))

        val lineOffSet = min(document.getLineStartOffset(startRow) + startCol, document.textLength - 1)
        val lineOffSetEnd = max(lineOffSet, min(document.getLineStartOffset(endRow) + endCol, document.textLength - 1))
        return TextRange.create(lineOffSet, lineOffSetEnd)
    }
}
