package snyk.common.lsp

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.Range

class RangeConverter {
    companion object {
        val logger = logger<RangeConverter>()
        /** Public for Tests only */
        fun convertToTextRange(
            psiFile: PsiFile,
            range: Range,
        ): TextRange? {
            try {
                val document =
                    psiFile.viewProvider.document ?: throw IllegalArgumentException("No document found for $psiFile")
                val startRow = range.start.line
                val endRow = range.end.line
                val startCol = range.start.character
                val endCol = range.end.character

                if (startRow < 0 || startRow > document.lineCount - 1) {
                    return null
                }
                if (endRow < 0 || endRow > document.lineCount - 1 || endRow < startRow) {
                    return null
                }

                val lineOffSet = document.getLineStartOffset(startRow) + startCol
                val lineOffSetEnd = document.getLineStartOffset(endRow) + endCol

                if (lineOffSet < 0 || lineOffSet > document.textLength - 1) {
                    return null
                }
                if (lineOffSetEnd < 0 || lineOffSetEnd < lineOffSet || lineOffSetEnd > document.textLength - 1) {
                    return null
                }

                return TextRange.create(lineOffSet, lineOffSetEnd)
            } catch (e: IllegalArgumentException) {
                logger.warn(e)
                return TextRange.EMPTY_RANGE
            }
        }
    }
}
