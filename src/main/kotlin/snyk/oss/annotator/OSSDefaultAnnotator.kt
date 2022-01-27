package snyk.oss.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import snyk.oss.Vulnerability

open class OSSDefaultAnnotator : OSSBaseAnnotator() {
    override fun lineMatches(psiFile: PsiFile, line: String, vulnerability: Vulnerability): Boolean {
        val fileName = psiFile.virtualFile.presentableUrl
        return hasDedicatedAnnotator(fileName) && super.lineMatches(psiFile, line, vulnerability)
    }

    private fun hasDedicatedAnnotator(fileName: String) =
        !fileName.endsWith("pom.xml") &&
            !fileName.endsWith("go.mod") &&
            !fileName.endsWith("build.gradle") &&
            !fileName.endsWith("build.gradle.kts")

    override fun fixRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        return TextRange.EMPTY_RANGE // don't offer fixes in default annotator
    }
}
