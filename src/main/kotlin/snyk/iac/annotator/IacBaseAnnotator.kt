package snyk.iac.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity.Companion.CRITICAL
import io.snyk.plugin.Severity.Companion.HIGH
import io.snyk.plugin.Severity.Companion.LOW
import io.snyk.plugin.Severity.Companion.MEDIUM
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.iac.IacIssue
import snyk.iac.IacResult

private val LOG = logger<IacBaseAnnotator>()

abstract class IacBaseAnnotator : ExternalAnnotator<PsiFile, Unit>() {

    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile? = file
    override fun doAnnotate(collectedInfo: PsiFile?): Unit = Unit

    fun getIssues(psiFile: PsiFile): List<IacIssue> {
        LOG.debug("Calling doAnnotate on ${psiFile.name}")

        val iacResult = psiFile.project.service<SnykToolWindowPanel>().currentIacResult ?: return emptyList()
        ProgressManager.checkCanceled()
        return getIssuesForFile(psiFile, iacResult)
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        val issues = getIssues(psiFile)

        LOG.debug("Call apply on ${psiFile.name}")
        if (issues == null || issues.isEmpty()) return

        LOG.debug("Received ${issues.size} IacIssue annotations for ${psiFile.virtualFile.name}")
        issues.forEach { iacIssue ->
            LOG.debug("-> ${iacIssue.id}: ${iacIssue.title}: ${iacIssue.lineNumber}")

            val severity = when (iacIssue.severity) {
                CRITICAL -> HighlightSeverity.ERROR
                HIGH -> HighlightSeverity.WARNING
                MEDIUM -> HighlightSeverity.WEAK_WARNING
                LOW -> HighlightSeverity.INFORMATION
                else -> HighlightSeverity.INFORMATION
            }
            holder.newAnnotation(severity, annotationMessage(iacIssue))
                .range(textRange(psiFile, iacIssue))
                .create()
        }
    }

    abstract fun textRange(psiFile: PsiFile, iacIssue: IacIssue): TextRange

    private fun getIssuesForFile(psiFile: PsiFile, iacResult: IacResult): List<IacIssue> {
        LOG.debug("Calling getAnnotationForFile for ${psiFile.virtualFile?.path}")

        val psiFilePath = psiFile.virtualFile?.path?.let { FileUtil.toSystemIndependentName(it) }
            ?: return emptyList()

        val iacIssuesForFile = iacResult.allCliIssues?.firstOrNull { iacIssuesForFile ->
            val iacTargetFilePath = FileUtil.toSystemIndependentName(iacIssuesForFile.targetFilePath)
            psiFilePath == iacTargetFilePath
        }
        LOG.debug("Found IaC issues for file: ${iacIssuesForFile?.targetFile} - ${iacIssuesForFile?.uniqueCount}")

        return iacIssuesForFile?.infrastructureAsCodeIssues?.toList() ?: emptyList()
    }

    fun annotationMessage(iacIssue: IacIssue): String {
        return buildString {
            append("Snyk - ")
            append(iacIssue.title)
        }
    }

    fun defaultTextRange(psiFile: PsiFile, iacIssue: IacIssue): TextRange {
        val document = psiFile.viewProvider.document ?: return TextRange.EMPTY_RANGE

        val startOffset = document.getLineStartOffset(iacIssue.lineNumber - 1)
        val endOffset = document.getLineEndOffset(iacIssue.lineNumber - 1)
        return TextRange.create(startOffset, endOffset)
    }
}
