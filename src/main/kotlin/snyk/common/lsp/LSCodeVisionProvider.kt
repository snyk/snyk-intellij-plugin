package snyk.common.lsp

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
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
import icons.SnykIcons
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val CODELENS_FETCH_TIMEOUT = 15L

@Suppress("UnstableApiUsage")
class LSCodeVisionProvider : CodeVisionProvider<Unit> {
    private val logger = logger<LSCodeVisionProvider>()
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Default
    override val id = "snyk.common.lsp.LSCodeVisionProvider"
    override val name = "Snyk Language Server Code Vision Provider"
    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = emptyList()

    override fun precomputeOnUiThread(editor: Editor) {}

    override fun isAvailableFor(project: Project): Boolean {
        return LanguageServerWrapper.getInstance().isInitialized
    }

    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
        LanguageServerWrapper.getInstance().ensureLanguageServerInitialized()
        return ReadAction.compute<CodeVisionState, RuntimeException> {
            val project = editor.project ?: return@compute CodeVisionState.READY_EMPTY
            val document = editor.document
            val file = PsiDocumentManager.getInstance(project).getPsiFile(document)
                ?: return@compute CodeVisionState.READY_EMPTY
            val params = CodeLensParams(TextDocumentIdentifier(file.virtualFile.url))
            val lenses = mutableListOf<Pair<TextRange, CodeVisionEntry>>()
            val codeLenses = try {
                LanguageServerWrapper.getInstance().languageServer.textDocumentService.codeLens(params)
                    .get(CODELENS_FETCH_TIMEOUT, TimeUnit.SECONDS)
            } catch (ignored: TimeoutException) {
                logger.warn("Timeout fetching code lenses for : $file")
                emptyList()
            }

            if (codeLenses == null) {
                return@compute CodeVisionState.READY_EMPTY
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
            return@compute CodeVisionState.Ready(lenses)
        }
    }

    private class LSCommandExecutionHandler(private val codeLens: CodeLens) : (MouseEvent?, Editor) -> Unit {
        override fun invoke(event: MouseEvent?, editor: Editor) {
            event ?: return

            val task = object : Backgroundable(editor.project, "Executing ${codeLens.command.title}", false) {
                override fun run(indicator: ProgressIndicator) {
                    val params = ExecuteCommandParams(codeLens.command.command, codeLens.command.arguments)
                    LanguageServerWrapper.getInstance().languageServer.workspaceService.executeCommand(params)
                }
            }

            ProgressManager.getInstance().run(task)
        }
    }
}
