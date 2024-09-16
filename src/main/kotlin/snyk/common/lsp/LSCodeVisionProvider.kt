package snyk.common.lsp

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import icons.SnykIcons
import io.snyk.plugin.toLanguageServerURL
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val CODELENS_FETCH_TIMEOUT = 10L

@Suppress("UnstableApiUsage")
class LSCodeVisionProvider : CodeVisionProvider<Unit>, CodeVisionGroupSettingProvider {
    private val logger = logger<LSCodeVisionProvider>()
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Default
    override val id = "snyk.common.lsp.LSCodeVisionProvider"
    override val name = "Snyk Security Language Server Code Vision Provider"
    override val groupId: String = "Snyk Security"
    override val groupName: String = groupId

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = emptyList()

    override fun precomputeOnUiThread(editor: Editor) {}

    override fun isAvailableFor(project: Project): Boolean {
        return true
    }

    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
        if (LanguageServerWrapper.getInstance().isDisposed()) return CodeVisionState.READY_EMPTY
        if (!LanguageServerWrapper.getInstance().isInitialized) return CodeVisionState.READY_EMPTY
        val project = editor.project ?: return CodeVisionState.READY_EMPTY

        val document = editor.document

        val file = ReadAction.compute<PsiFile, RuntimeException> {
            PsiDocumentManager.getInstance(project).getPsiFile(document)
        } ?: return CodeVisionState.READY_EMPTY

        val params = CodeLensParams(TextDocumentIdentifier(file.virtualFile.toLanguageServerURL()))
        val lenses = mutableListOf<Pair<TextRange, CodeVisionEntry>>()
        val codeLenses = try {
            LanguageServerWrapper.getInstance().languageServer.textDocumentService.codeLens(params)
                .get(CODELENS_FETCH_TIMEOUT, TimeUnit.SECONDS) ?: return CodeVisionState.READY_EMPTY
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

            val task = object : Backgroundable(editor.project, "Executing ${codeLens.command.title}", false) {
                override fun run(indicator: ProgressIndicator) {
                    val params = ExecuteCommandParams(codeLens.command.command, codeLens.command.arguments)
                    try {
                        LanguageServerWrapper.getInstance().languageServer.workspaceService
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
