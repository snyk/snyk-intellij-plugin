package snyk.oss.annotator

import com.intellij.json.JsonFileType
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.siblings
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability

class OSSNpmAnnotator : OSSBaseAnnotator() {

    override fun textRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        if (psiFile.fileType !is JsonFileType || psiFile.name != "package.json") return TextRange.EMPTY_RANGE
        val currentVersion = getIntroducingPackageVersion(vulnerability)
        val packageName = getIntroducingPackage(vulnerability)
        val visitor = NpmRecursiveVisitor(packageName, currentVersion)
        psiFile.accept(visitor)
        return visitor.foundTextRange
    }

    override fun getFixVersion(
        remediation: OssVulnerabilitiesForFile.Remediation?,
        vulnerability: Vulnerability
    ): String {
        val fixVersion = super.getFixVersion(remediation, vulnerability)
        return if (fixVersion.isNotBlank()) {
            "\"$fixVersion\""
        } else {
            fixVersion
        }
    }

    internal class NpmRecursiveVisitor(
        private val packageName: String, private val artifactVersion: String
    ) : JsonRecursiveElementVisitor() {

        var foundTextRange: TextRange = TextRange.EMPTY_RANGE

        override fun visitElement(element: PsiElement) {
            if (isSearchedDependency(element)) {
                val siblings = element.siblings()
                siblings.forEach {
                    if (it is JsonStringLiteral && it.value == artifactVersion) {
                        this.foundTextRange = TextRange(it.textRange.startOffset, it.textRange.endOffset)
                        return
                    }
                }
            }
            super.visitElement(element)
        }

        private fun isSearchedDependency(element: PsiElement): Boolean {
            return element is JsonStringLiteral && element.value == packageName
        }
    }
}
