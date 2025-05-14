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
        // Our LS is coded to give us the TextEdits in ascending order, but we must apply them in descending order.
        // Imagine we had an edit that added 10 lines to the start of the file followed by an edit that added a line to the 4th line of the file,
        // ascending order applying would incorrectly place the second edit withing the 10 lines added at the start!
        // N.b. Line edits on a single line are returned as two edits, a deletion of the line and an addition to the line after, so reversing works for this too.
        // N.b. The previous note is a lie for edits at the end of the file when no LF at EOF is present, for those the addition is marked for the same line as the deletion.
        // TODO - Handle edits at EOF when there is no LF at EOF.
        for (e in change.value.reversed()) {
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
