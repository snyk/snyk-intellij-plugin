package snyk.oss.annotator

import com.intellij.psi.PsiFile
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability

class OSSGoModAnnotator : OSSBaseAnnotator() {
    override fun lineMatches(psiFile: PsiFile, line: String, vulnerability: Vulnerability): Boolean {
        val fileName = psiFile.virtualFile.path
        return fileName.endsWith("go.mod") && !line.endsWith("// indirect") &&
            super.lineMatches(psiFile, line, vulnerability)
    }

    override fun getFixVersion(
        remediation: OssVulnerabilitiesForFile.Remediation,
        vulnerability: Vulnerability
    ): String {
        return super.getFixVersion(remediation, vulnerability).replace("@", " v")
    }

    override fun getIntroducingPackage(vulnerability: Vulnerability): String {
        return sanitize(super.getIntroducingPackage(vulnerability))
    }

    private fun sanitize(string: String): String {
        return string.replace("/pkg/tool", "")
    }

    override fun colEnd(line: String, vulnerability: Vulnerability): Int = line.length
}
