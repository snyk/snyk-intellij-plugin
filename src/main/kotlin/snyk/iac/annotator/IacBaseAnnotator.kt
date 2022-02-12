package snyk.iac.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity.Companion.CRITICAL
import io.snyk.plugin.Severity.Companion.HIGH
import io.snyk.plugin.Severity.Companion.LOW
import io.snyk.plugin.Severity.Companion.MEDIUM
import io.snyk.plugin.getSnykToolWindowPanel
import snyk.iac.IacIssue
import snyk.iac.IacResult

private val LOG = logger<IacBaseAnnotator>()

abstract class IacBaseAnnotator : ExternalAnnotator<PsiFile, Unit>() {

    // override needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile? = file

    // save all changes on disk to update caches through SnykBulkFileListener
    override fun doAnnotate(collectedInfo: PsiFile?) {
        LOG.debug("Calling doAnnotate on ${collectedInfo?.name}")
        val psiFile = collectedInfo ?: return
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: return
        ApplicationManager.getApplication().invokeLater {
            FileDocumentManager.getInstance().saveDocument(document)
        }
    }

    fun getIssues(psiFile: PsiFile): List<IacIssue> {
        val iacResult = getSnykToolWindowPanel(psiFile.project)?.currentIacResult ?: return emptyList()
        ProgressManager.checkCanceled()
        return getIssuesForFile(psiFile, iacResult)
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        val issues = getIssues(psiFile)

        LOG.debug("Call apply on ${psiFile.name}")
        if (issues.isEmpty()) return

        LOG.debug("Received ${issues.size} IacIssue annotations for ${psiFile.virtualFile.name}")
        issues.forEach { iacIssue ->
            if (iacIssue.ignored || iacIssue.obsolete) return@forEach

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

        val lineNumber = iacIssue.lineNumber - 1
        if (lineNumber < 0 || document.lineCount <= lineNumber) return TextRange.EMPTY_RANGE

        val startOffset = document.getLineStartOffset(lineNumber)
        val endOffset = document.getLineEndOffset(lineNumber)
        return TextRange.create(startOffset, endOffset)
    }
}
