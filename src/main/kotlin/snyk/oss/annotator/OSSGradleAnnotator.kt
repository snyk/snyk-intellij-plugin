package snyk.oss.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiWhiteSpace
import snyk.oss.Vulnerability

class OSSGradleAnnotator : OSSBaseAnnotator() {

    override fun getIntroducingPackage(vulnerability: Vulnerability): String {
        return super.getIntroducingPackage(vulnerability)
            .replace("'", "").replace("\"", "")
    }

    override fun textRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        if (psiFile.name != "build.gradle" && psiFile.name != "build.gradle.kts") return TextRange.EMPTY_RANGE
        val currentVersion = getIntroducingPackageVersion(vulnerability)
        val packageName = getIntroducingPackage(vulnerability)
        val visitor = GradleRecursiveVisitor(packageName, currentVersion)
        psiFile.accept(visitor)
        return visitor.foundTextRange
    }

    internal class GradleRecursiveVisitor(
        private val packageName: String, private val version: String
    ) : PsiRecursiveElementVisitor() {

        var foundTextRange: TextRange = TextRange.EMPTY_RANGE

        override fun visitElement(element: PsiElement) {
            if (isSearchedDependency(element)) {
                val indexOf = element.text.indexOf(version)
                if (indexOf > 0) {
                    this.foundTextRange =
                        TextRange(element.textRange.startOffset + indexOf, element.textRange.endOffset)
                    return
                }
            }
            super.visitElement(element)
        }

        private fun isSearchedDependency(element: PsiElement): Boolean {
            return element !is PsiComment && element !is PsiWhiteSpace && "$packageName:$version" == element.text
        }
    }
}
