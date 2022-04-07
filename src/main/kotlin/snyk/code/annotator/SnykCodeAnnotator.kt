package snyk.code.annotator

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.SnykCodeFile
import snyk.common.AnnotatorCommon

class SnykCodeAnnotator : ExternalAnnotator<PsiFile, Unit>() {
    val logger = logger<SnykCodeAnnotator>()
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
                    holder.newAnnotation(severity, annotationMessage(suggestionForFile))
                        .range(textRange)
                        .create()
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
        return AnalysisData.instance.getAnalysis(SnykCodeFile(psiFile.project, psiFile.virtualFile))
    }

    fun annotationMessage(suggestion: SuggestionForFile): String {
        return buildString {
            val title = if (suggestion.title.isNotBlank()) " " + suggestion.title + "." else ""
            append("Snyk Code:$title ${suggestion.message}")
        }
    }

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
}
