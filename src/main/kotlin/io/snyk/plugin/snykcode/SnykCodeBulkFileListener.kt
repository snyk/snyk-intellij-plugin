package io.snyk.plugin.snykcode

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.readText
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.SnykFile
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.toLanguageServerURL
import io.snyk.plugin.toSnykFileSet
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.lsp.LanguageServerWrapper

class SnykCodeBulkFileListener : SnykBulkFileListener() {
    override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) = Unit

    override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        val filesAffected = toSnykFileSet(project, virtualFilesAffected)

        if (isSnykCodeLSEnabled()) {
            updateCacheAndUI(filesAffected, project)
            return
        }
    }

    override fun forwardEvents(events: MutableList<out VFileEvent>) {
        val languageServerWrapper = LanguageServerWrapper.getInstance()

        if (!isSnykCodeLSEnabled()) return
        if (!languageServerWrapper.isInitialized) return

        val languageServer = languageServerWrapper.languageServer
        for (event in events) {
            if (event.file == null || !event.isFromSave) continue
            val file = event.file!!
            val activeProject = ProjectUtil.getActiveProject()
            ProgressManager.getInstance().run(object : Backgroundable(
                activeProject,
                "Snyk: forwarding save event to Language Server"
            ) {
                override fun run(indicator: ProgressIndicator) {
                    val param = DidSaveTextDocumentParams(TextDocumentIdentifier(file.toLanguageServerURL()), file.readText())
                    languageServer.textDocumentService.didSave(param)
                }
            })
        }
    }

    private fun updateCacheAndUI(filesAffected: Set<SnykFile>, project: Project) {
        val cache = getSnykCachedResults(project)?.currentSnykCodeResultsLS ?: return
        filesAffected.forEach {
            cache.remove(it)
        }
        VirtualFileManager.getInstance().asyncRefresh()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
}
