package snyk.code.annotator

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.getDocument
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import snyk.common.AnnotatorCommon
import snyk.common.intentionactions.ShowDetailsIntentionActionBase
import snyk.common.intentionactions.SnykIntentionActionBase
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue
import java.util.concurrent.TimeUnit
import javax.swing.Icon

private const val TIMEOUT = 120L

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
            .sortedBy { it.title }
            .forEach { issue ->
                val highlightSeverity = issue.getSeverityAsEnum().getHighlightSeverity()
                val textRange = textRange(psiFile, issue.range)
                if (!textRange.isEmpty) {
                    val annotationMessage = "${annotationMessage(issue)} (Snyk)"
                    holder.newAnnotation(highlightSeverity, annotationMessage)
                        .range(textRange)
                        .withFix(ShowDetailsIntentionAction(annotationMessage, issue))
                        .create()

                    val params = CodeActionParams(
                        TextDocumentIdentifier(psiFile.virtualFile.url),
                        issue.range,
                        CodeActionContext(emptyList())
                    )
                    val languageServer = LanguageServerWrapper.getInstance().languageServer
                    val codeActions = languageServer.textDocumentService
                        .codeAction(params).get(2, TimeUnit.SECONDS)

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
                                .withFix(CodeActionIntention(issue, codeAction))
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
    fun textRange(psiFile: PsiFile, range: Range): TextRange {
        try {
            val document =
                psiFile.viewProvider.document ?: throw IllegalArgumentException("No document found for $psiFile")
            val startRow = range.start.line
            val endRow = range.end.line
            val startCol = range.start.character
            val endCol = range.end.character

            if (startRow < 0 || startRow > document.lineCount - 1) {
                throw IllegalArgumentException("Invalid range $range")
            }
            if (endRow < 0 || endRow > document.lineCount - 1 || endRow < startRow) {
                throw IllegalArgumentException("Invalid range $range")
            }

            val lineOffSet = document.getLineStartOffset(startRow) + startCol
            val lineOffSetEnd = document.getLineStartOffset(endRow) + endCol

            if (lineOffSet < 0 || lineOffSet > document.textLength - 1) {
                throw IllegalArgumentException("Invalid range $range")
            }
            if (lineOffSetEnd < 0 || lineOffSetEnd < lineOffSet || lineOffSetEnd > document.textLength - 1) {
                throw IllegalArgumentException("Invalid range $range")
            }

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

    inner class CodeActionIntention(private val issue: ScanIssue, private val codeAction: CodeAction) : SnykIntentionActionBase() {
        private var changes: Map<String, List<TextEdit>>? = null

        override fun getText(): String = codeAction.title

        override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
            val task = object : Backgroundable(project, "Applying Snyk Code Action") {
                override fun run(p0: ProgressIndicator) {
                    val languageServer = LanguageServerWrapper.getInstance().languageServer
                    var resolvedCodeAction = codeAction
                    if (codeAction.command == null && codeAction.edit == null) {
                        resolvedCodeAction =
                            languageServer.textDocumentService
                                .resolveCodeAction(codeAction).get(TIMEOUT, TimeUnit.SECONDS)

                        val edit = resolvedCodeAction.edit
                        if (edit.changes == null) return
                        changes = edit.changes
                    } else {
                        val codeActionCommand = resolvedCodeAction.command
                        val executeCommandParams =
                            ExecuteCommandParams(codeActionCommand.command, codeActionCommand.arguments)
                        languageServer.workspaceService
                            .executeCommand(executeCommandParams).get(TIMEOUT, TimeUnit.SECONDS)
                    }
                }

                override fun onSuccess() {
                    // see https://intellij-support.jetbrains.com/hc/en-us/community/posts/360005049419-Run-WriteAction-in-Background-process-Asynchronously
                    // save the write action to call it later onSuccess
                    // as we are updating documents, we need a WriteCommandAction
                    WriteCommandAction.runWriteCommandAction(project) {
                        if (changes == null) return@runWriteCommandAction
                        for (change in changes!!) {
                            val fileURI = change.key
                            val virtualFile = VirtualFileManager.getInstance().findFileByUrl(fileURI) ?: continue
                            val document = virtualFile.getDocument() ?: continue
                            for (e in change.value) {
                                // normalize range
                                var startLine = e.range.start.line
                                var startCharacter = e.range.start.character
                                var endLine = e.range.end.line
                                var endCharacter = e.range.end.character

                                if (startLine < 0) startLine = 0
                                if (endLine > document.lineCount) {
                                    endLine = document.lineCount - 1
                                    endCharacter =
                                        document.getLineEndOffset(endLine) - document.getLineStartOffset(endLine)
                                }

                                val startLineOffset = document.getLineStartOffset(startLine)
                                val endLineOffset = document.getLineStartOffset(endLine)

                                if (startLineOffset + startCharacter > document.getLineEndOffset(startLine)) {
                                    startCharacter = document.getLineEndOffset(startLine)
                                }
                                if (endLineOffset + endCharacter > document.getLineEndOffset(endLine)) {
                                    endCharacter = document.getLineEndOffset(endLine)
                                }

                                val start = document.getLineStartOffset(startLine) + startCharacter
                                val end = document.getLineStartOffset(endLine) + endCharacter

                                document.replaceString(start, end, e.newText)
                            }
                        }
                    }
                }
            }
            ProgressManager.getInstance().run(task)
        }

        override fun getIcon(p0: Int): Icon = SnykIcons.SNYK_CODE

        override fun getPriority(): PriorityAction.Priority {
            return when {
                codeAction.title.contains("fix", ignoreCase = true) -> PriorityAction.Priority.TOP
                else -> issue.getSeverityAsEnum().getQuickFixPriority()
            }
        }
    }
}
