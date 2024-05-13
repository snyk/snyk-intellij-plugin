package snyk.code.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.toLanguageServerURL
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.AnnotatorCommon
import snyk.common.ProductType
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val CODEACTION_TIMEOUT = 2L

class SnykCodeAnnotatorLS : ExternalAnnotator<PsiFile, Unit>() {
    val logger = logger<SnykCodeAnnotatorLS>()

    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile {
        return file
    }

    override fun doAnnotate(psiFile: PsiFile?) {
        AnnotatorCommon.prepareAnnotate(psiFile)
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        if (!isSnykCodeLSEnabled()) return
        if (!LanguageServerWrapper.getInstance().ensureLanguageServerInitialized()) return
        if (isSnykCodeRunning(psiFile.project)) return

        getIssuesForFile(psiFile)
            .filter { AnnotatorCommon.isSeverityToShow(it.getSeverityAsEnum()) }
            .distinctBy { it.id }
            .sortedBy { it.title }
            .forEach { issue ->
                val highlightSeverity = issue.getSeverityAsEnum().getHighlightSeverity()
                val textRange = textRange(psiFile, issue.range)
                if (textRange == null) {
                    logger.warn("Invalid range for issue: $issue")
                    return@forEach
                }
                if (!textRange.isEmpty) {
                    val annotationMessage = "${annotationMessage(issue)} (Snyk)"
                    holder.newAnnotation(highlightSeverity, annotationMessage)
                        .range(textRange)
                        .withFix(ShowDetailsIntentionAction(annotationMessage, issue))
                        .create()

                    val params = CodeActionParams(
                        TextDocumentIdentifier(psiFile.virtualFile.toLanguageServerURL()),
                        issue.range,
                        CodeActionContext(emptyList())
                    )
                    val languageServer = LanguageServerWrapper.getInstance().languageServer
                    val codeActions = try {
                        languageServer.textDocumentService
                            .codeAction(params).get(CODEACTION_TIMEOUT, TimeUnit.SECONDS) ?: emptyList()
                    } catch (ignored: TimeoutException) {
                        logger.info("Timeout fetching code actions for issue: $issue")
                        emptyList()
                    }

                    codeActions
                        .filter { a ->
                            val diagnosticCode = a.right.diagnostics?.get(0)?.code?.left
                            val ruleId = issue.additionalData.ruleId
                            diagnosticCode == ruleId
                        }
                        .sortedBy { it.right.title }.forEach { action ->
                            val codeAction = action.right
                            val title = codeAction.title
                            holder.newAnnotation(highlightSeverity, title)
                                .range(textRange)
                                .withFix(CodeActionIntention(issue, codeAction, ProductType.CODE_SECURITY))
                                .create()
                        }
                }
            }
    }

    private fun getIssuesForFile(psiFile: PsiFile): Set<ScanIssue> =
        getSnykCachedResults(psiFile.project)?.currentSnykCodeResultsLS
            ?.filter { it.key.virtualFile == psiFile.virtualFile }
            ?.map { it.value }
            ?.flatten()
            ?.toSet()
            ?: emptySet()

    /** Public for Tests only */
    fun annotationMessage(issue: ScanIssue): String =
        issue.title.ifBlank {
            issue.additionalData.message.let {
                if (it.length < 70) it else "${it.take(70)}..."
            }
        }.split(":")[0]

    /** Public for Tests only */
    @Suppress("DuplicatedCode")
    fun textRange(psiFile: PsiFile, range: Range): TextRange? {
        try {
            val document =
                psiFile.viewProvider.document ?: throw IllegalArgumentException("No document found for $psiFile")
            val startRow = range.start.line
            val endRow = range.end.line
            val startCol = range.start.character
            val endCol = range.end.character

            if (startRow < 0 || startRow > document.lineCount - 1) {
                return null
            }
            if (endRow < 0 || endRow > document.lineCount - 1 || endRow < startRow) {
                return null
            }

            val lineOffSet = document.getLineStartOffset(startRow) + startCol
            val lineOffSetEnd = document.getLineStartOffset(endRow) + endCol

            if (lineOffSet < 0 || lineOffSet > document.textLength - 1) {
                return null
            }
            if (lineOffSetEnd < 0 || lineOffSetEnd < lineOffSet || lineOffSetEnd > document.textLength - 1) {
                return null
            }

            return TextRange.create(lineOffSet, lineOffSetEnd)
        } catch (e: IllegalArgumentException) {
            logger.warn(e)
            return TextRange.EMPTY_RANGE
        }
    }
}
