package snyk.oss.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import snyk.oss.Vulnerability

class OSSGradleAnnotator : OSSBaseAnnotator() {

    override fun lineMatches(psiFile: PsiFile, line: String, vulnerability: Vulnerability) =
        (psiFile.virtualFile.name == "build.gradle" || psiFile.virtualFile.name == "build.gradle.kts") &&
            super.lineMatches(psiFile, line, vulnerability)

    override fun getIntroducingPackage(vulnerability: Vulnerability): String {
        return super.getIntroducingPackage(vulnerability)
            .replace("'", "").replace("\"", "")
    }

    override fun fixRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        val textRange = super.textRange(psiFile, vulnerability)
        val currentVersion = getIntroducingPackageVersion(vulnerability)
        val searchedVersionLocation = textRange.endOffset - currentVersion.length
        return TextRange(searchedVersionLocation, textRange.endOffset)
    }
}
