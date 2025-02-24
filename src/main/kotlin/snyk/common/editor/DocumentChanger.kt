package snyk.common.editor

import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.getDocument
import org.eclipse.lsp4j.TextEdit

object DocumentChanger {
    fun applyChange(change: Map.Entry<String, List<TextEdit>>?) {
        if (change == null) return //TODO add log
        val fileURI = change.key
        val virtualFile = VirtualFileManager.getInstance().findFileByUrl(fileURI) ?: return
        val document = virtualFile.getDocument() ?: return
        for (e in change.value) {
            // normalize range
            var startLine = e.range.start.line
            var startCharacter = e.range.start.character
            var endLine = e.range.end.line
            var endCharacter = e.range.end.character

            if (startLine < 0) startLine = 0
            if (endLine > document.lineCount) {
                endLine = document.lineCount - 1
                endCharacter =
                    document.getLineEndOffset(endLine) - document.getLineStartOffset(endLine)
            }

            val startLineOffset = document.getLineStartOffset(startLine)
            val endLineOffset = document.getLineStartOffset(endLine)

            if (startLineOffset + startCharacter > document.getLineEndOffset(startLine)) {
                startCharacter = document.getLineEndOffset(startLine) - startLineOffset
            }
            if (endLineOffset + endCharacter > document.getLineEndOffset(endLine)) {
                endCharacter = document.getLineEndOffset(endLine) - endLineOffset
            }

            val start = document.getLineStartOffset(startLine) + startCharacter
            val end = document.getLineStartOffset(endLine) + endCharacter

            document.replaceString(start, end, e.newText)
        }
    }
}
