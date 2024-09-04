package snyk.code.annotator

import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectForFile
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import icons.SnykIcons
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.toLanguageServerURL
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.code.annotator.SnykAnnotator.SnykAnnotation
import snyk.common.AnnotatorCommon
import snyk.common.ProductType
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.Icon

private const val CODEACTION_TIMEOUT = 5000L

abstract class SnykAnnotator(private val product: ProductType) :
    ExternalAnnotator<Pair<PsiFile, List<ScanIssue>>, List<SnykAnnotation>>(), Disposable, DumbAware {
    val logger = logger<SnykAnnotator>()
    protected var disposed = false
        get() {
            return ApplicationManager.getApplication().isDisposed || field
        }

    fun isDisposed() = disposed

    override fun dispose() {
        disposed = true
    }

    inner class SnykAnnotation(
        val annotationSeverity: HighlightSeverity,
        val annotationMessage: String,
        val range: TextRange,
        val intention: IntentionAction,
        val renderGutterIcon: Boolean = false
    )

    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): Pair<PsiFile, List<ScanIssue>> {
        return Pair(file, getIssuesForFile(file)
            .filter { AnnotatorCommon.isSeverityToShow(it.getSeverityAsEnum()) }
            .distinctBy { it.id }
            .sortedBy { it.title })
    }

    override fun doAnnotate(initial: Pair<PsiFile, List<ScanIssue>>): List<SnykAnnotation> {
        if (disposed) return emptyList()
        AnnotatorCommon.prepareAnnotate(initial.first)
        if (!LanguageServerWrapper.getInstance().isInitialized) return emptyList()

        val annotations = mutableListOf<SnykAnnotation>()
        val gutterIcons : MutableSet<TextRange> = mutableSetOf()
        initial.second.sortedBy { it.getSeverityAsEnum().getHighlightSeverity() }.forEach { issue ->
            val textRange = textRange(initial.first, issue.range)
            val highlightSeverity = issue.getSeverityAsEnum().getHighlightSeverity()
            val annotationMessage = issue.annotationMessage()
            if (textRange == null) {
                logger.warn("Invalid range for issue: $issue")
                return@forEach
            }
            if (!textRange.isEmpty) {
                val detailAnnotation = SnykAnnotation(
                    highlightSeverity,
                    annotationMessage,
                    textRange,
                    ShowDetailsIntentionAction(annotationMessage, issue),
                    renderGutterIcon = !gutterIcons.contains(textRange)
                )
                annotations.add(detailAnnotation)
                gutterIcons.add(textRange)

                val params =
                    CodeActionParams(
                        TextDocumentIdentifier(initial.first.virtualFile.toLanguageServerURL()),
                        issue.range,
                        CodeActionContext(emptyList()),
                    )
                val languageServer = LanguageServerWrapper.getInstance().languageServer
                val codeActions =
                    try {
                        languageServer.textDocumentService
                            .codeAction(params).get(CODEACTION_TIMEOUT, TimeUnit.MILLISECONDS) ?: emptyList()
                    } catch (ignored: TimeoutException) {
                        logger.info("Timeout fetching code actions for issue: $issue")
                        emptyList()
                    }

                codeActions
                    .filter { a ->
                        val diagnosticCode = a.right.diagnostics?.get(0)?.code?.left
                        val ruleId = issue.ruleId()
                        diagnosticCode == ruleId
                    }
                    .sortedBy { it.right.title }.forEach { action ->
                        val codeAction = action.right
                        val title = codeAction.title
                        val codeActionAnnotation = SnykAnnotation(
                            highlightSeverity,
                            title,
                            textRange,
                            CodeActionIntention(issue, codeAction, product)
                        )
                        annotations.add(codeActionAnnotation)
                    }

            }
        }
        return annotations
    }

    override fun apply(
        psiFile: PsiFile,
        annotationResult: List<SnykAnnotation>,
        holder: AnnotationHolder,
    ) {
        if (disposed) return
        if (!LanguageServerWrapper.getInstance().isInitialized) return
        annotationResult.sortedBy { it.annotationSeverity }.forEach { annotation ->
            if (!annotation.range.isEmpty) {
                val annoBuilder = holder.newAnnotation(annotation.annotationSeverity, annotation.annotationMessage)
                    .range(annotation.range)
                    .withFix(annotation.intention)
                if (annotation.renderGutterIcon) {
                    annoBuilder.gutterIconRenderer(SnykShowDetailsGutterRenderer(annotation))
                }
                annoBuilder.create()
            }
        }
    }

    private fun getIssuesForFile(psiFile: PsiFile): Set<ScanIssue> =
        getSnykCachedResultsForProduct(psiFile.project, product)
            ?.filter { it.key.virtualFile == psiFile.virtualFile }
            ?.map { it.value }
            ?.flatten()
            ?.toSet()
            ?: emptySet()

    /** Public for Tests only */
    fun textRange(
        psiFile: PsiFile,
        range: Range,
    ): TextRange? {
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

class SnykShowDetailsGutterRenderer(val annotation: SnykAnnotation) : GutterIconRenderer() {
    override fun equals(other: Any?): Boolean {
        return annotation == other
    }

    override fun hashCode(): Int {
        return annotation.hashCode()
    }

    override fun getIcon(): Icon {
        return SnykIcons.TOOL_WINDOW
    }

    override fun getClickAction(): AnAction? {
        if (annotation.intention !is ShowDetailsIntentionAction) return null
        return object: AnAction() {
            override fun actionPerformed(e: AnActionEvent) {
                invokeLater {
                    val virtualFile = annotation.intention.issue.virtualFile ?: return@invokeLater
                    val project = guessProjectForFile(virtualFile) ?: return@invokeLater
                    val toolWindowPanel = getSnykToolWindowPanel(project) ?: return@invokeLater

                    annotation.intention.selectNodeAndDisplayDescription(toolWindowPanel)
                }

            }
        }
    }

    override fun isNavigateAction(): Boolean {
        return true
    }

    override fun isDumbAware(): Boolean {
        return true
    }


}
