package snyk.common.codevision

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.CodeVisionState
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import io.snyk.plugin.SnykFile
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.toLanguageServerURI
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.RangeConverter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Suppress("UnstableApiUsage")
class LSCodeVisionProvider : CodeVisionProvider<Unit>, CodeVisionGroupSettingProvider {
    private val logger = logger<LSCodeVisionProvider>()
    // Reduced timeout to prevent long UI freezes when LS is busy
    private val timeout = 2L
    override val defaultAnchor: CodeVisionAnchorKind = CodeVisionAnchorKind.Default
    override val id = "snyk.common.lsp.LSCodeVisionProvider"
    override val name = "Snyk Security Language Server Code Vision Provider"
    override val groupId: String = "Snyk Security"
    override val groupName: String = groupId

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = emptyList()

    override fun precomputeOnUiThread(editor: Editor) {}

    override fun isAvailableFor(project: Project): Boolean {
        val pluginSettings = pluginSettings()
        return pluginSettings.ossScanEnable || pluginSettings.snykCodeSecurityIssuesScanEnable || pluginSettings.iacScanEnabled
    }

    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
        val project = editor.project ?: return CodeVisionState.READY_EMPTY
        if (project.isDisposed) return CodeVisionState.READY_EMPTY
        
        val lsWrapper = LanguageServerWrapper.getInstance(project)
        // Early return if LS is not ready - prevents blocking calls during initialization
        if (lsWrapper.isDisposed() || !lsWrapper.isInitialized) return CodeVisionState.READY_EMPTY

        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return CodeVisionState.READY_EMPTY
        
        // Skip isInContent check - if we have cached results for this file, it's valid
        // The cache only contains files that passed isInContent when diagnostics were published
        val snykFile = SnykFile(project, virtualFile)
        val cachedResults = getSnykCachedResults(project)
        val hasResults = cachedResults?.currentOSSResultsLS?.get(snykFile)?.isNotEmpty() == true ||
            cachedResults?.currentSnykCodeResultsLS?.get(snykFile)?.isNotEmpty() == true ||
            cachedResults?.currentIacResultsLS?.get(snykFile)?.isNotEmpty() == true
        if (!hasResults) return CodeVisionState.READY_EMPTY

        val params = CodeLensParams(TextDocumentIdentifier(virtualFile.toLanguageServerURI()))
        val lenses = mutableListOf<Pair<TextRange, CodeVisionEntry>>()
        val codeLenses = try {
            lsWrapper.languageServer.textDocumentService.codeLens(params)
                .get(timeout, TimeUnit.SECONDS) ?: return CodeVisionState.READY_EMPTY
        } catch (_: TimeoutException) {
            logger.info("Timeout fetching code lenses for : $virtualFile")
            return CodeVisionState.READY_EMPTY
        }

        codeLenses.forEach { codeLens ->
            val range = RangeConverter.convertToTextRange(document, codeLens.range) ?: return@forEach

            val entry = ClickableTextCodeVisionEntry(
                text = codeLens.command.title,
                providerId = id,
                onClick = LSCommandExecutionHandler(codeLens),
                extraActions = emptyList()
            )
            lenses.add(range to entry)
        }
        return CodeVisionState.Ready(lenses)
    }

    private class LSCommandExecutionHandler(private val codeLens: CodeLens) : (MouseEvent?, Editor) -> Unit {
        private val logger = logger<LSCommandExecutionHandler>()
        override fun invoke(event: MouseEvent?, editor: Editor) {
            event ?: return
            val project = editor.project ?: return
            val lsWrapper = LanguageServerWrapper.getInstance(project)
            if (lsWrapper.isDisposed() || !lsWrapper.isInitialized) return

            val task = object : Task.Backgroundable(project, "Executing ${codeLens.command.title}", false) {
                override fun run(indicator: ProgressIndicator) {
                    val params = ExecuteCommandParams(codeLens.command.command, codeLens.command.arguments)
                    try {
                        lsWrapper.languageServer.workspaceService
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
