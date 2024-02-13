package snyk.code.annotator

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.AnnotatorCommon
import snyk.common.intentionactions.ShowDetailsIntentionActionBase
import snyk.common.intentionactions.SnykIntentionActionBase
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue
import java.util.concurrent.TimeUnit
import javax.swing.Icon

class SnykCodeAnnotatorLS : ExternalAnnotator<PsiFile, Unit>() {
    val logger = logger<SnykCodeAnnotatorLS>()

    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile {
        LanguageServerWrapper.getInstance().ensureLanguageServerInitialized()
        return file
    }

    override fun doAnnotate(psiFile: PsiFile?) {
        AnnotatorCommon.prepareAnnotate(psiFile)
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        if (!isSnykCodeLSEnabled()) return
        getIssuesForFile(psiFile)
            .filter { AnnotatorCommon.isSeverityToShow(it.getSeverityAsEnum()) }
            .forEach { issue ->
                val highlightSeverity = issue.getSeverityAsEnum().getHighlightSeverity()
                val textRange = textRange(psiFile, issue.range)
                if (!textRange.isEmpty) {
                    val annotationMessage = annotationMessage(issue)
                    holder.newAnnotation(highlightSeverity, "Snyk: $annotationMessage")
                        .range(textRange)
                        .withFix(ShowDetailsIntentionAction(annotationMessage, issue))
                        .create()

                    val params = CodeActionParams(
                        TextDocumentIdentifier(issue.virtualFile!!.url), issue.range, CodeActionContext(
                            emptyList()
                        )
                    )
                    val languageServer = LanguageServerWrapper.getInstance().languageServer
                    val codeActions = languageServer.textDocumentService.codeAction(params).get()
                    codeActions.distinct().forEach { action ->
                        holder.newAnnotation(highlightSeverity, action.right.title)
                            .range(textRange)
                            .withFix(CodeActionIntention(action.right))
                            .create()
                    }
                }
            }
    }

    private fun getIssuesForFile(psiFile: PsiFile): List<ScanIssue> =
        getSnykCachedResults(psiFile.project)?.currentSnykCodeResultsLS
            ?.filter { it.key.virtualFile == psiFile.virtualFile }
            ?.map { it.value }
            ?.flatten()
            ?.toList()
            ?: emptyList()

    /** Public for Tests only */
    fun annotationMessage(issue: ScanIssue): String =
        issue.title.ifBlank {
            issue.additionalData.message.let {
                if (it.length < 70) it else "${it.take(70)}..."
            }
        }.split(":")[0]

    /** Public for Tests only */
    @Suppress("DuplicatedCode")
    fun textRange(psiFile: PsiFile, range: Range): TextRange {
        try {
            val document =
                psiFile.viewProvider.document ?: throw IllegalArgumentException("No document found for $psiFile")
            val startRow = range.start.line
            val endRow = range.end.line
            val startCol = range.start.character
            val endCol = range.end.character

            if (startRow < 0 || startRow > document.lineCount - 1)
                throw IllegalArgumentException("Invalid range $range")
            if (endRow < 0 || endRow > document.lineCount - 1 || endRow < startRow)
                throw IllegalArgumentException("Invalid range $range")

            val lineOffSet = document.getLineStartOffset(startRow) + startCol
            val lineOffSetEnd = document.getLineStartOffset(endRow) + endCol

            if (lineOffSet < 0 || lineOffSet > document.textLength - 1)
                throw IllegalArgumentException("Invalid range $range")
            if (lineOffSetEnd < 0 || lineOffSetEnd < lineOffSet || lineOffSetEnd > document.textLength - 1)
                throw IllegalArgumentException("Invalid range $range")

            return TextRange.create(lineOffSet, lineOffSetEnd)
        } catch (e: IllegalArgumentException) {
            logger.warn(e)
            return TextRange.EMPTY_RANGE
        }
    }

    inner class ShowDetailsIntentionAction(
        override val annotationMessage: String,
        private val issue: ScanIssue
    ) : ShowDetailsIntentionActionBase() {
        override fun selectNodeAndDisplayDescription(toolWindowPanel: SnykToolWindowPanel) {
            toolWindowPanel.selectNodeAndDisplayDescription(issue)
        }

        override fun getSeverity(): Severity = issue.getSeverityAsEnum()
    }

    inner class CodeActionIntention(private val codeAction: CodeAction) : SnykIntentionActionBase() {
        override fun getText(): String = codeAction.title

        override fun invoke(p0: Project, p1: Editor?, psiFile: PsiFile?) {
            val languageServer = LanguageServerWrapper.getInstance().languageServer
            var resolvedCodeAction = codeAction
            if (codeAction.command == null) {
                resolvedCodeAction =
                    languageServer.textDocumentService.resolveCodeAction(codeAction).get(30, TimeUnit.SECONDS)
                val edit = resolvedCodeAction.edit ?: return
                edit.changes?.forEach { (key, edit) ->
                    edit.forEach {
                        val document = psiFile?.viewProvider?.document ?: return
                        val start = 0
                        val end = document.textLength
                        document.replaceString(start, end, it.newText)
                    }
                }
            } else {
                val codeActionCommand = resolvedCodeAction.command ?: return
                val executeCommandParams = ExecuteCommandParams(codeActionCommand.command, codeActionCommand.arguments)
                languageServer.workspaceService.executeCommand(executeCommandParams).get(30, TimeUnit.SECONDS)
            }
        }

        override fun getIcon(p0: Int): Icon = SnykIcons.SNYK_CODE

        override fun getPriority() = PriorityAction.Priority.NORMAL
    }
}
