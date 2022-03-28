package snyk.oss.annotator

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import snyk.common.intentionactions.AlwaysAvailableReplacementIntentionAction
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability

class OSSNpmAnnotator : OSSBaseAnnotator() {

    override fun textRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        if (psiFile.fileType !is JsonFileType || psiFile.name != "package.json") return TextRange.EMPTY_RANGE
        val packageName = getIntroducingPackage(vulnerability)
        val visitor = NpmRecursiveVisitor(packageName)
        psiFile.accept(visitor)
        return visitor.foundTextRange
    }

    override fun addQuickFix(
        psiFile: PsiFile,
        vulnerability: Vulnerability,
        annotationBuilder: AnnotationBuilder,
        textRange: TextRange,
        fixVersion: String
    ) {
        if (fixVersion.isNotBlank()) {
            val msg = if (psiFile.parent?.findFile("package-lock.json") != null) {
                "Please update your package-lock.json to finish fixing the vulnerability."
            } else {
                ""
            }
            annotationBuilder.withFix(
                AlwaysAvailableReplacementIntentionAction(
                    textRange,
                    fixVersion,
                    message = msg
                )
            )
        }
    }

    override fun getFixVersion(
        remediation: OssVulnerabilitiesForFile.Remediation?,
        vulnerability: Vulnerability
    ): String {
        val upgrade = getUpgradeProposal(vulnerability, remediation)
        val split = upgrade?.upgradeTo?.split("@") ?: return ""
        return "\"${split[0]}\": \"${split[1]}\""
    }

    internal class NpmRecursiveVisitor(private val artifactName: String) : JsonRecursiveElementVisitor() {

        var foundTextRange: TextRange = TextRange.EMPTY_RANGE

        override fun visitElement(element: PsiElement) {
            if (isSearchedDependency(element)) {
                val value = element.getNextSiblingIgnoringWhitespace()?.getNextSiblingIgnoringWhitespace() ?: return
                this.foundTextRange = TextRange(element.textRange.startOffset, value.textRange.endOffset)
                return
            }
            super.visitElement(element)
        }

        private fun PsiElement.getNextSiblingIgnoringWhitespace(): PsiElement? {
            var candidate = this.nextSibling
            while (candidate is PsiWhiteSpace) {
                candidate = candidate.nextSibling
            }
            return candidate
        }

        private fun isSearchedDependency(element: PsiElement): Boolean {
            return element !is PsiComment && element !is PsiWhiteSpace &&
                element is JsonStringLiteral &&
                element.value == artifactName
        }
    }
}
