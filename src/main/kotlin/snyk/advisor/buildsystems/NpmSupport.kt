package snyk.advisor.buildsystems

import com.intellij.json.JsonElementTypes
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import kotlin.math.min

class NpmSupport(private val editor: Editor) {

    fun getPackageName(lineNumber: Int): String? {
        val document = editor.document
        val project = editor.project ?: return null
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
        val lineStartElement = psiFile.findElementAt(document.getLineStartOffset(lineNumber))
            ?: return null

        // line should start with PsiWhiteSpace following by JsonProperty
        // or with JsonProperty itself (represented by leaf with type JsonElementTypes.DOUBLE_QUOTED_STRING)
        val name2versionPropertyElement: JsonProperty = when {
            lineStartElement is PsiWhiteSpace &&
                // check for multi-line PsiWhiteSpace element (i.e. few empty lines)
                document.getLineNumberChecked(lineStartElement.textRange.endOffset) == lineNumber
            -> (lineStartElement.nextSibling as? JsonProperty) ?: return null

            lineStartElement is LeafPsiElement &&
                lineStartElement.elementType == JsonElementTypes.DOUBLE_QUOTED_STRING &&
                // see(PsiViewer) Psi representation of package dependencies in package.json, i.e. "adm-zip": "0.4.7"
                lineStartElement.parent is JsonStringLiteral &&
                lineStartElement.parent.parent is JsonProperty
            -> lineStartElement.parent.parent as JsonProperty

            else -> return null
        }

        if (!isInsideNPMDependencies(name2versionPropertyElement)) return null

        // don't show Scores if few packages are on the same line
        val prevName2VersionElement =
            PsiTreeUtil.getPrevSiblingOfType(name2versionPropertyElement, JsonProperty::class.java)
        if (prevName2VersionElement != null &&
            document.getLineNumberChecked(prevName2VersionElement.textRange.endOffset) == lineNumber) return null

        val nextName2VersionElement =
            PsiTreeUtil.getNextSiblingOfType(name2versionPropertyElement, JsonProperty::class.java)

        if (nextName2VersionElement != null &&
            document.getLineNumberChecked(nextName2VersionElement.textRange.endOffset) == lineNumber) return null

        // see(PsiViewer) Psi representation of package dependencies in package.json, i.e. "adm-zip": "0.4.7"
        return if (name2versionPropertyElement.firstChild is JsonStringLiteral &&
            name2versionPropertyElement.firstChild.firstChild is LeafPsiElement &&
            (name2versionPropertyElement.firstChild.firstChild as LeafPsiElement).elementType == JsonElementTypes.DOUBLE_QUOTED_STRING
        ) {
            name2versionPropertyElement.firstChild.text.removeSurrounding("\"")
        } else null
    }

    // see(PsiViewer) Psi representation of "dependencies" in package.json
    private fun isInsideNPMDependencies(element: JsonProperty) =
        element.parent is JsonObject &&
            element.parent.parent is JsonProperty &&
            element.parent.parent.firstChild is JsonStringLiteral &&
            element.parent.parent.firstChild.textMatches("\"dependencies\"")

    private fun Document.getLineNumberChecked(offset: Int): Int = getLineNumber(min(offset, textLength))
}
