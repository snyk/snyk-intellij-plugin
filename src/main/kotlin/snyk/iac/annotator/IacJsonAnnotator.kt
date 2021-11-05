package snyk.iac.annotator

import com.intellij.json.psi.JsonElement
import com.intellij.json.psi.JsonProperty
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.siblings
import snyk.iac.IacIssue

private val LOG = logger<IacJsonAnnotator>()

class IacJsonAnnotator : IacBaseAnnotator() {
    override fun textRange(psiFile: PsiFile, iacIssue: IacIssue): TextRange {
        var textRange = defaultTextRange(psiFile, iacIssue)

        // try to guess precise start/end offset positions
        when (val element = getNextJsonElement(psiFile.viewProvider.findElementAt(textRange.startOffset))) {
            is JsonProperty -> textRange = highlightingForJsonProperty(element)
        }

        LOG.debug("Calculated text range for JSON ${iacIssue.id} - $textRange")
        return textRange
    }

    private fun highlightingForJsonProperty(jsonProperty: JsonProperty): TextRange {
        return jsonProperty.nameElement.textRange
    }

    private fun getNextJsonElement(psiElement: PsiElement?): JsonElement? {
        val element = psiElement?.siblings(forward = true, withSelf = false)?.firstOrNull { it is JsonElement }
        return element?.let { it as JsonElement }
    }
}
