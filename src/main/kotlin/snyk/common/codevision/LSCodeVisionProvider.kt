package snyk.common.codevision

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import icons.SnykIcons
import io.snyk.plugin.isInContent
import io.snyk.plugin.toLanguageServerURI
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.lsp.LanguageServerWrapper
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Suppress("UnstableApiUsage")
class LSCodeVisionProvider : CodeVisionProvider<Unit>, CodeVisionGroupSettingProvider {
    private val logger = logger<LSCodeVisionProvider>()
    private val timeout = 10L
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Default
    override val id = "snyk.common.lsp.LSCodeVisionProvider"
    override val name = "Snyk Security Language Server Code Vision Provider"
    override val groupId: String = "Snyk Security"
    override val groupName: String = groupId

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = emptyList()

    override fun precomputeOnUiThread(editor: Editor) {}

    override fun isAvailableFor(project: Project): Boolean = true

    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
        val project = editor.project ?: return CodeVisionState.READY_EMPTY
        if (LanguageServerWrapper.getInstance(project).isDisposed()) return CodeVisionState.READY_EMPTY
        if (!LanguageServerWrapper.getInstance(project).isInitialized) return CodeVisionState.READY_EMPTY
        val document = editor.document

        val file = ReadAction.compute<PsiFile, RuntimeException> {
            PsiDocumentManager.getInstance(project).getPsiFile(document)
        } ?: return CodeVisionState.READY_EMPTY

        val virtualFile = file.virtualFile
        if (!virtualFile.isInContent(project)) return CodeVisionState.READY_EMPTY

        val params = CodeLensParams(TextDocumentIdentifier(file.virtualFile.toLanguageServerURI()))
        val lenses = mutableListOf<Pair<TextRange, CodeVisionEntry>>()
        val codeLenses = try {
            LanguageServerWrapper.getInstance(project).languageServer.textDocumentService.codeLens(params)
                .get(timeout, TimeUnit.SECONDS) ?: return CodeVisionState.READY_EMPTY
        } catch (ignored: TimeoutException) {
            logger.info("Timeout fetching code lenses for : $file")
            return CodeVisionState.READY_EMPTY
        }

        codeLenses.forEach { codeLens ->
            val range = TextRange(
                document.getLineStartOffset(codeLens.range.start.line) + codeLens.range.start.character,
                document.getLineEndOffset(codeLens.range.end.line) + codeLens.range.end.character
            )

            val entry = ClickableTextCodeVisionEntry(
                text = codeLens.command.title,
                providerId = id,
                onClick = LSCommandExecutionHandler(codeLens),
                extraActions = emptyList(),
                icon = SnykIcons.TOOL_WINDOW
            )
            lenses.add(range to entry)
        }
        return CodeVisionState.Ready(lenses)
    }

    private class LSCommandExecutionHandler(private val codeLens: CodeLens) : (MouseEvent?, Editor) -> Unit {
        private val logger = logger<LSCommandExecutionHandler>()
        override fun invoke(event: MouseEvent?, editor: Editor) {
            event ?: return

            val task = object : Task.Backgroundable(editor.project, "Executing ${codeLens.command.title}", false) {
                override fun run(indicator: ProgressIndicator) {
                    val params = ExecuteCommandParams(codeLens.command.command, codeLens.command.arguments)
                    try {
                        LanguageServerWrapper.getInstance(project).languageServer.workspaceService
                            .executeCommand(params).get(2, TimeUnit.MINUTES)
                    } catch (e: TimeoutException) {
                        logger.error("Timeout executing: ${codeLens.command.title}", e)
                    } finally {
                        indicator.stop()
                    }
                }
            }

            ProgressManager.getInstance().run(task)
        }
    }
}
