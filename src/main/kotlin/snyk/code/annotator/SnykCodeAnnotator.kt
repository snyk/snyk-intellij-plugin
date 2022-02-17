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
        val startRow = max(0, snykCodeRange.startRow - 1)
        val endRow = min(document.lineCount, snykCodeRange.endRow - 1)
        val endCol = min(document.getLineEndOffset(endRow), snykCodeRange.endCol)
        val lineOffSet = document.getLineStartOffset(startRow) + snykCodeRange.startCol
        val lineOffSetEnd = document.getLineStartOffset(endRow) + endCol
        return TextRange.create(lineOffSet, lineOffSetEnd)
    }
}
