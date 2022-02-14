package snyk.oss.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiWhiteSpace
import snyk.oss.Vulnerability

class OSSGradleKtsAnnotator : OSSBaseAnnotator() {

    override fun getIntroducingPackage(vulnerability: Vulnerability): String {
        return super.getIntroducingPackage(vulnerability)
            .replace("'", "").replace("\"", "")
    }

    override fun textRange(psiFile: PsiFile, vulnerability: Vulnerability) =
        findTextRanges(psiFile, vulnerability).first

    private fun findTextRanges(
        psiFile: PsiFile,
        vulnerability: Vulnerability
    ): Pair<TextRange, TextRange> {
        if (!psiFile.name.startsWith("build.gradle")) return Pair(
            TextRange.EMPTY_RANGE,
            TextRange.EMPTY_RANGE
        )
        val currentVersion = getIntroducingPackageVersion(vulnerability)
        val packageName = getIntroducingPackage(vulnerability)
        val visitor = GradleRecursiveVisitor(packageName, currentVersion)
        psiFile.accept(visitor)
        return Pair(visitor.artifactTextRange, visitor.versionTextRange)
    }

    override fun fixRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange =
        findTextRanges(psiFile, vulnerability).second

    internal class GradleRecursiveVisitor(
        private val packageName: String, private val version: String
    ) : PsiRecursiveElementVisitor() {

        var artifactTextRange: TextRange = TextRange.EMPTY_RANGE
        var versionTextRange: TextRange = TextRange.EMPTY_RANGE

        override fun visitElement(element: PsiElement) {
            if (isSearchedDependency(element)) {
                val endOffset = element.textRange.endOffset
                this.artifactTextRange = TextRange(element.textRange.startOffset, endOffset)
                val indexOf = element.text.indexOf(version)
                if (indexOf > 0) {
                    versionTextRange = TextRange(element.textRange.startOffset + indexOf, endOffset)
                }
                return
            }
            super.visitElement(element)
        }

        private fun isSearchedDependency(element: PsiElement): Boolean {
            if (element is PsiComment || element is PsiWhiteSpace || element is PsiFile) return false
            // no version given
            val depGroups = element.text.split(":")
            return if (depGroups.size > 2) {
                "$packageName:$version" == element.text
            } else {
                packageName == element.text
            }
        }
    }
}
