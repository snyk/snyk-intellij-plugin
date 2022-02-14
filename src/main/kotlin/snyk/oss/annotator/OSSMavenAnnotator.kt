package snyk.oss.annotator

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.util.siblings
import com.intellij.psi.xml.XmlTag
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability

class OSSMavenAnnotator : OSSBaseAnnotator() {

    override fun getIntroducingPackage(vulnerability: Vulnerability): String {
        return super.getIntroducingPackage(vulnerability).split(":")[1].replace("\"", "")
    }

    override fun textRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        return fixRange(psiFile, vulnerability)
    }

    override fun getFixVersion(
        remediation: OssVulnerabilitiesForFile.Remediation?,
        vulnerability: Vulnerability
    ): String {
        // we need to use the super class, as we need the group for finding the upgrade
        val key = super.getIntroducingPackage(vulnerability) + "@" + super.getIntroducingPackageVersion(vulnerability)
        return remediation?.upgrade?.get(key)?.upgradeTo?.split("@")?.get(1) ?: ""
    }

    override fun fixRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        if (psiFile.fileType !is XmlFileType || psiFile.name != "pom.xml") return TextRange.EMPTY_RANGE
        val currentVersion = getIntroducingPackageVersion(vulnerability)
        val artifactName = getIntroducingPackage(vulnerability)
        val visitor = MavenRecursiveVisitor(artifactName, currentVersion)
        psiFile.accept(visitor)
        return visitor.foundTextRange
    }

    internal class MavenRecursiveVisitor(
        private val artifactName: String, private val artifactVersion: String
    ) : XmlRecursiveElementVisitor() {

        var foundTextRange: TextRange = TextRange.EMPTY_RANGE

        override fun visitElement(element: PsiElement) {
            if (isSearchedDependency(element)) {
                val siblings = element.siblings()
                siblings.forEach {
                    if (it is XmlTag && it.name == "version" && it.value.text == artifactVersion) {
                        this.foundTextRange = TextRange(it.value.textRange.startOffset, it.value.textRange.endOffset)
                        return
                    }
                }
            }
            super.visitElement(element)
        }

        private fun isSearchedDependency(element: PsiElement): Boolean {
            return element is XmlTag && element.name == "artifactId" && element.value.text == artifactName
                && element.parent is XmlTag && (element.parent as XmlTag).name == "dependency"
        }
    }
}
