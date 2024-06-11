package io.snyk.plugin.snykcode

import com.google.common.cache.CacheBuilder
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.readText
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.toLanguageServerURL
import io.snyk.plugin.toSnykFileSet
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.lsp.LanguageServerWrapper
import java.time.Duration

class SnykCodeBulkFileListener : SnykBulkFileListener() {
    // Cache for debouncing file updates that come in within one second of the last
    // Key = path, Value is irrelevant
    private val debounceFileCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(1000)).build<String, Boolean>()

    override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) = Unit

    override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        if (virtualFilesAffected.isEmpty()) return
        ProgressManager.getInstance().run(object : Backgroundable(
            project,
            "Snyk: forwarding save event to Language Server"
        ) {
            override fun run(indicator: ProgressIndicator) {
                val languageServerWrapper = LanguageServerWrapper.getInstance()
                if (!languageServerWrapper.isInitialized) return
                val languageServer = languageServerWrapper.languageServer
                val cache = getSnykCachedResults(project)?.currentSnykCodeResultsLS ?: return
                val filesAffected = toSnykFileSet(project, virtualFilesAffected)
                for (file in filesAffected) {
                    val virtualFile = file.virtualFile
                    if (!shouldProcess(virtualFile)) continue
                    cache.remove(file)
                    val param =
                        DidSaveTextDocumentParams(
                            TextDocumentIdentifier(virtualFile.toLanguageServerURL()),
                            virtualFile.readText()
                        )
                    languageServer.textDocumentService.didSave(param)
                }

                VirtualFileManager.getInstance().asyncRefresh()
                runInEdt { DaemonCodeAnalyzer.getInstance(project).restart() }
            }
        })

    }

    private fun shouldProcess(file: VirtualFile): Boolean {
        val inCache = debounceFileCache.getIfPresent(file.path)

        return if (inCache != null) {
            logger<SnykCodeBulkFileListener>().info("not forwarding file event to ls, debouncing")
            false
        } else {
            debounceFileCache.put(file.path, true)
            logger<SnykCodeBulkFileListener>().info("forwarding file event to ls, not debouncing")
            true
        }
    }

    override fun forwardEvents(events: MutableList<out VFileEvent>) = Unit
}
