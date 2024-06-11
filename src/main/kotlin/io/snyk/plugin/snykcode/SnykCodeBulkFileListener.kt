package io.snyk.plugin.snykcode

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.runInEdt
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

class SnykCodeBulkFileListener : SnykBulkFileListener() {
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
                    cache.remove(file)
                    val param =
                        DidSaveTextDocumentParams(TextDocumentIdentifier(virtualFile.toLanguageServerURL()), virtualFile.readText())
                    languageServer.textDocumentService.didSave(param)
                }
                VirtualFileManager.getInstance().asyncRefresh()
                runInEdt { DaemonCodeAnalyzer.getInstance(project).restart() }
            }
        })
    }

    override fun forwardEvents(events: MutableList<out VFileEvent>) = Unit
}
