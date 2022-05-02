package snyk.oss.annotator

import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.getSnykCachedResults
import snyk.common.AnnotatorCommon
import snyk.common.intentionactions.AlwaysAvailableReplacementIntentionAction
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability
import snyk.oss.annotator.AnnotatorHelper.severity
import kotlin.math.max

abstract class OSSBaseAnnotator : ExternalAnnotator<PsiFile, Unit>() {
    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile = file
    override fun doAnnotate(psiFile: PsiFile?) {
        val filePath = psiFile?.virtualFile?.path ?: return

        if (AnnotatorHelper.isFileSupported(filePath)) {
            AnnotatorCommon.prepareAnnotate(psiFile)
        }
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        val issues = getIssuesForFile(psiFile) ?: return

        issues.vulnerabilities
            .forEach { vulnerability ->
                if (vulnerability.ignored || vulnerability.obsolete) return@forEach
                val textRange = textRange(psiFile, vulnerability)
                val severity = severity(vulnerability)
                if (textRange != TextRange.EMPTY_RANGE) {
                    val annotationBuilder =
                        holder.newAnnotation(severity, annotationMessage(vulnerability)).range(textRange)
                    val fixRange = fixRange(psiFile, vulnerability)
                    val fixVersion = getFixVersion(issues.remediation, vulnerability)
                    if (fixRange != TextRange.EMPTY_RANGE && fixVersion.isNotBlank()) {
                        addQuickFix(psiFile, vulnerability, annotationBuilder, fixRange, fixVersion)
                    }
                    annotationBuilder.create()
                }
            }
    }

    open fun getIssuesForFile(psiFile: PsiFile): OssVulnerabilitiesForFile? {
        val ossResult = getSnykCachedResults(psiFile.project)?.currentOssResults ?: return null
        val filePath = psiFile.virtualFile?.path ?: return null
        if (!AnnotatorHelper.isFileSupported(filePath)) return null

        ProgressManager.checkCanceled()

        return ossResult.allCliIssues
            ?.firstOrNull { filePath.endsWith(it.displayTargetFile.replace("-lock", "")) }
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
        fixVersion: String
    ) {
        if (fixVersion.isNotBlank()) {
            annotationBuilder.withFix(
                AlwaysAvailableReplacementIntentionAction(
                    textRange,
                    fixVersion
                )
            )
        }
    }

    open fun getFixVersion(remediation: OssVulnerabilitiesForFile.Remediation?, vulnerability: Vulnerability): String {
        val upgrade = getUpgradeProposal(vulnerability, remediation)
        return upgrade?.upgradeTo?.split("@")?.get(1) ?: ""
    }

    open fun getUpgradeProposal(
        vulnerability: Vulnerability,
        remediation: OssVulnerabilitiesForFile.Remediation?
    ): OssVulnerabilitiesForFile.Upgrade? {
        val upgradeKey = getIntroducingPackage(vulnerability) + "@" + getIntroducingPackageVersion(vulnerability)
        return remediation?.upgrade?.get(upgradeKey)
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
        return if (hasNoIntroducingPackage(vulnerability)) {
            vulnerability.packageName
        } else {
            vulnerability.from[1].split("@")[0]
        }
    }

    open fun getIntroducingPackageVersion(vulnerability: Vulnerability): String {
        return if (hasNoIntroducingPackage(vulnerability)) {
            vulnerability.version
        } else {
            vulnerability.from[1].split("@")[1]
        }
    }

    private fun hasNoIntroducingPackage(vulnerability: Vulnerability) =
        vulnerability.from.isEmpty() || vulnerability.from.size < 2

    open fun lineMatches(psiFile: PsiFile, line: String, vulnerability: Vulnerability): Boolean =
        line.contains(getIntroducingPackage(vulnerability))
}
