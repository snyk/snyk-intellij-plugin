package snyk.advisor

import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.json.JsonElementTypes
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

class SnykAdvisorEditorLinePainter : EditorLinePainter() {

    override fun getLineExtensions(project: Project, file: VirtualFile, lineNumber: Int): Collection<LineExtensionInfo>? {
        if (file.name != "package.json") return null
        val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val lineStartElement = psiFile.findElementAt(document.getLineStartOffset(lineNumber)) ?: return null

        val packageName = getPackageName(lineStartElement) ?: return null
        val score = service<SnykAdvisorModel>().getScore(project, packageName) ?: return null

        if (score > 70) return null

        return listOf(
            LineExtensionInfo("    \\\\ ", getNormalAttributes()),
            LineExtensionInfo("\u26A0", getWarningIconAttributes()),
            LineExtensionInfo(" Advisor Score $score/100", getNormalAttributes())
        )
    }

    private fun getPackageName(lineStartElement: PsiElement): String? {
        // see(PsiViewer) Psi representation of "dependencies" in package.json
        val packageJsonPropertyElement =
            if (lineStartElement is PsiWhiteSpace &&
                lineStartElement.nextSibling is JsonProperty &&
                lineStartElement.parent is JsonObject &&
                lineStartElement.parent.parent is JsonProperty &&
                lineStartElement.parent.parent.firstChild is JsonStringLiteral &&
                lineStartElement.parent.parent.firstChild.textMatches("\"dependencies\"")
            ) {
                lineStartElement.nextSibling
            } else return null

        // see(PsiViewer) Psi representation of package dependencies in package.json, i.e. "adm-zip": "0.4.7"
        return if (packageJsonPropertyElement.firstChild is JsonStringLiteral &&
            packageJsonPropertyElement.firstChild.firstChild is LeafPsiElement &&
            packageJsonPropertyElement.firstChild.firstChild.elementType == JsonElementTypes.DOUBLE_QUOTED_STRING
        ) {
            packageJsonPropertyElement.firstChild.text.removeSurrounding("\"")
        } else return null
    }

    companion object {
        /** see [com.intellij.xdebugger.impl.evaluate.XDebuggerEditorLinePainter.getNormalAttributes] */
        private fun getNormalAttributes(): TextAttributes {
            val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(DefaultLanguageHighlighterColors.BLOCK_COMMENT)
            return if (attributes == null || attributes.foregroundColor == null) {
                TextAttributes(
                    JBColor { if (EditorColorsManager.getInstance().isDarkEditor) Color(0x3d8065) else Gray._135 },
                    null,
                    null,
                    null,
                    Font.ITALIC
                )
            } else attributes
        }

        private fun getWarningIconAttributes(): TextAttributes {
            val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(ConsoleHighlighter.YELLOW)
            return if (attributes == null || attributes.foregroundColor == null) {
                TextAttributes(
                    JBColor { if (EditorColorsManager.getInstance().isDarkEditor) Color.YELLOW.darker() else Color.YELLOW },
                    null,
                    null,
                    null,
                    Font.PLAIN
                )
            } else attributes
        }
    }
}
