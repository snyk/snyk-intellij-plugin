package snyk.oss.annotator

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.getSnykToolWindowPanel
import snyk.common.intentionactions.AlwaysAvailableReplacementIntentionAction
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability
import snyk.oss.annotator.AnnotatorHelper.severity
import kotlin.math.max

abstract class OSSBaseAnnotator : ExternalAnnotator<PsiFile, Unit>() {
    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile = file
    override fun doAnnotate(collectedInfo: PsiFile?): Unit = Unit

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        val issues = getIssuesForFile(psiFile)
        val remediation = issues?.remediation

        issues?.vulnerabilities
            ?.distinctBy { it.id }
            ?.forEach { vulnerability ->
                if (vulnerability.ignored || vulnerability.obsolete) return@forEach

                val severity = severity(vulnerability)
                val textRange = textRange(psiFile, vulnerability)
                val annotationBuilder =
                    holder.newAnnotation(severity, annotationMessage(vulnerability)).range(textRange)
                val fixRange = fixRange(psiFile, vulnerability)
                if (fixRange != TextRange.EMPTY_RANGE && remediation != null && remediation.upgrade.isNotEmpty()) {
                    addQuickFix(psiFile, vulnerability, annotationBuilder, fixRange, remediation)
                }
                annotationBuilder.create()
            }
    }

    open fun getIssuesForFile(psiFile: PsiFile): OssVulnerabilitiesForFile? {
        val ossResult = getSnykToolWindowPanel(psiFile.project)?.currentOssResults
        val fileName = psiFile.virtualFile.presentableUrl

        ProgressManager.checkCanceled()

        return ossResult?.allCliIssues
            ?.filter { AnnotatorHelper.isFileSupported(fileName) }
            ?.firstOrNull { fileName.endsWith(it.displayTargetFile) }
    }

    open fun annotationMessage(vulnerability: Vulnerability): String {
        return buildString {
            append("Snyk: ${vulnerability.title} in ${vulnerability.name}")
        }
    }

    open fun addQuickFix(
        psiFile: PsiFile,
        vulnerability: Vulnerability,
        annotationBuilder: AnnotationBuilder,
        textRange: TextRange,
        remediation: OssVulnerabilitiesForFile.Remediation
    ) {
        val replacementText = getFixVersion(remediation, vulnerability)
        if (replacementText.isNotBlank()) {
            annotationBuilder.withFix(AlwaysAvailableReplacementIntentionAction(textRange, replacementText))
        }
    }

    open fun getFixVersion(remediation: OssVulnerabilitiesForFile.Remediation, vulnerability: Vulnerability): String {
        val upgrade = remediation.upgrade[vulnerability.from[1]]
        return upgrade?.upgradeTo?.split("@")?.get(1) ?: ""
    }

    open fun textRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange {
        val document = psiFile.viewProvider.document ?: return TextRange.EMPTY_RANGE
        val lines = document.text.lines()
        var lineStart = 0
        var lineEnd = 0
        var colStart = 0
        var colEnd = 0
        for (i in lines.indices) {
            val line = lines[i]
            if (lineMatches(psiFile, line, vulnerability)) {
                lineStart = i
                lineEnd = i
                colStart = colStart(line, vulnerability)
                colEnd = colEnd(line, vulnerability)
                break
            }
        }
        val lineOffSet = document.getLineStartOffset(lineStart) + colStart
        val lineOffSetEnd = document.getLineStartOffset(lineEnd) + colEnd
        return TextRange.create(lineOffSet, lineOffSetEnd)
    }

    open fun fixRange(psiFile: PsiFile, vulnerability: Vulnerability): TextRange = textRange(psiFile, vulnerability)

    open fun colEnd(line: String, vulnerability: Vulnerability): Int {
        val versionIndex = line.indexOf(getIntroducingPackageVersion(vulnerability))
        return if (versionIndex == -1) {
            line.length
        } else {
            versionIndex + getIntroducingPackageVersion(vulnerability).length
        }
    }

    open fun colStart(line: String, vulnerability: Vulnerability) =
        max(0, line.indexOf(getIntroducingPackage(vulnerability)))

    open fun getIntroducingPackage(vulnerability: Vulnerability): String {
        return if (vulnerability.from.isEmpty()) {
            vulnerability.packageName
        } else {
            vulnerability.from[1].split("@")[0]
        }
    }

    open fun getIntroducingPackageVersion(vulnerability: Vulnerability): String {
        return if (vulnerability.from.isEmpty()) {
            vulnerability.version
        } else {
            vulnerability.from[1].split("@")[1]
        }
    }

    open fun lineMatches(psiFile: PsiFile, line: String, vulnerability: Vulnerability): Boolean =
        line.contains(getIntroducingPackage(vulnerability))
}
