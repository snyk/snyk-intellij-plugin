package snyk.oss.annotator

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import snyk.oss.Vulnerability

class OSSMavenAnnotator : OSSBaseAnnotator() {

    override fun lineMatches(psiFile: PsiFile, line: String, vulnerability: Vulnerability) =
        psiFile.virtualFile.name == "pom.xml" &&
            line.contains("<artifactId>${getIntroducingPackage(vulnerability)}</artifactId>")

    override fun getIntroducingPackage(vulnerability: Vulnerability): String {
        return super.getIntroducingPackage(vulnerability).split(":")[1].replace("\"", "")
    }

    override fun textRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        return fixRange(psiFile, vulnerability)
    }

    override fun fixRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        val textRange = super.textRange(psiFile, vulnerability)
        val text = psiFile.viewProvider.document?.text ?: return TextRange.EMPTY_RANGE // this should never happen

        val currentVersion = getIntroducingPackageVersion(vulnerability)
        val endOfDependencyBlock = text.substring(textRange.endOffset).indexOf("</dependency>")
        val searchedVersionLocation = text.substring(textRange.endOffset).indexOf(currentVersion)

        // only add quickfix if the version is explicit within dependency - no resolution of variables
        return if (searchedVersionLocation == -1 || searchedVersionLocation > endOfDependencyBlock) {
            TextRange.EMPTY_RANGE
        } else {
            val versionStart = textRange.endOffset + searchedVersionLocation
            val versionEnd = versionStart + currentVersion.length
            TextRange(versionStart, versionEnd)
        }
    }
}
