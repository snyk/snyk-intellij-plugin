package snyk.iac.annotator

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import org.intellij.terraform.hcl.psi.HCLBlock
import org.intellij.terraform.hcl.psi.HCLElement
import org.intellij.terraform.hcl.psi.HCLProperty
import snyk.iac.IacIssue

private val LOG = logger<IacHclAnnotator>()

class IacHclAnnotator : IacBaseAnnotator() {
    override fun textRange(psiFile: PsiFile, iacIssue: IacIssue): TextRange {
        var textRange = defaultTextRange(psiFile, iacIssue)

        val element = getNextHCLElement(psiFile.viewProvider.findElementAt(textRange.startOffset))
        if (element != null) textRange = element.textRange

        LOG.debug("Calculated text range for ${iacIssue.id}: $textRange")
        return textRange
    }

    private fun getNextHCLElement(psiElement: PsiElement?): PsiElement? {
        if (psiElement.elementType.toString() == "ID") {
            return psiElement as LeafPsiElement
        }

        return when (val nextSibling = psiElement?.nextSiblingNonWhiteSpace()) {
            is HCLBlock -> nextSibling.firstChild
            is HCLProperty -> nextSibling.firstChild
            is HCLElement -> nextSibling.nextSibling
            else -> psiElement
        }
    }

    private fun PsiElement.nextSiblingNonWhiteSpace(): PsiElement? {
        var next = this.nextSibling
        while (next != null && next is PsiWhiteSpace) {
            next = next.nextSibling
        }
        return next
    }
}
