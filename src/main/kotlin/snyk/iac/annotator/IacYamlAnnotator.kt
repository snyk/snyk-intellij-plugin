package snyk.iac.annotator

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.siblings
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLSequence
import snyk.iac.IacIssue

private val LOG = logger<IacYamlAnnotator>()

class IacYamlAnnotator : IacBaseAnnotator() {
    override fun textRange(psiFile: PsiFile, iacIssue: IacIssue): TextRange {
        var textRange = defaultTextRange(psiFile, iacIssue)

        // try to guess precise start/end offset positions
        when (val element = getNextYAMLElement(psiFile.viewProvider.findElementAt(textRange.startOffset))) {
            is YAMLKeyValue -> textRange = highlightingForYAMLKeyValue(element)
            is YAMLMapping -> textRange = highlightingForYAMLMapping(element)
            is YAMLSequence -> textRange = highlightingForYAMLSequence(element)
        }

        LOG.debug("Calculated text range for ${iacIssue.id}: $textRange")
        return textRange
    }

    private fun highlightingForYAMLKeyValue(yamlKeyValue: YAMLKeyValue): TextRange {
        val startOffset = yamlKeyValue.key?.startOffset ?: yamlKeyValue.startOffset
        val endOffset = yamlKeyValue.key?.endOffset ?: yamlKeyValue.endOffset
        return TextRange.create(startOffset, endOffset)
    }

    private fun highlightingForYAMLMapping(mapping: YAMLMapping): TextRange {
        val mappingItem = mapping.firstChild
        return if (mappingItem is YAMLKeyValue) {
            highlightingForYAMLKeyValue(mappingItem)
        } else {
            val startOffset = mapping.firstChild.startOffset
            val endOffset = mapping.firstChild.endOffset
            TextRange.create(startOffset, endOffset)
        }
    }

    private fun highlightingForYAMLSequence(yamlSequence: YAMLSequence): TextRange {
        val sequenceItem = yamlSequence.items.firstOrNull()
        val startOffset = sequenceItem?.keysValues?.firstOrNull()?.key?.startOffset ?: yamlSequence.startOffset
        val endOffset = sequenceItem?.keysValues?.firstOrNull()?.key?.endOffset ?: yamlSequence.endOffset
        return TextRange.create(startOffset, endOffset)
    }

    private fun getNextYAMLElement(psiElement: PsiElement?): YAMLPsiElement? {
        val element = psiElement?.siblings(forward = true, withSelf = false)?.firstOrNull { it is YAMLPsiElement }
        return element?.let { it as YAMLPsiElement }
    }
}
