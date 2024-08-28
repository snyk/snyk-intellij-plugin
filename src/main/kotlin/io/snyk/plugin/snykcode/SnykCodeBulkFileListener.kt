package io.snyk.plugin.snykcode

import com.google.common.cache.CacheBuilder
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
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
import org.jetbrains.concurrency.runAsync
import snyk.common.lsp.LanguageServerWrapper
import java.io.File
import java.time.Duration

class SnykCodeBulkFileListener : SnykBulkFileListener() {
    // Cache for debouncing file updates that come in within one second of the last
    // Key = path, Value is irrelevant
    private val debounceFileCache =
        CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMillis(1000)).build<String, Boolean>()

    private val blackListedDirectories =
        setOf(".idea", ".git", ".hg", ".svn")

    override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) = Unit

    override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        if (virtualFilesAffected.isEmpty()) return

        runAsync {
            if (ApplicationManager.getApplication().isDisposed) return@runAsync
            if (project.isDisposed) return@runAsync
            if (DumbService.getInstance(project).isDumb) return@runAsync

            val languageServerWrapper = LanguageServerWrapper.getInstance()
            if (languageServerWrapper.isDisposed() || !languageServerWrapper.isInitialized) return@runAsync

            val languageServer = languageServerWrapper.languageServer
            val cache = getSnykCachedResults(project)?.currentSnykCodeResultsLS
            val filesAffected = toSnykFileSet(project, virtualFilesAffected)
            val index = ProjectFileIndex.getInstance(project)

            for (file in filesAffected) {
                val virtualFile = file.virtualFile
                if (!shouldProcess(virtualFile, index, project)) continue
                cache?.remove(file)
                val param =
                    DidSaveTextDocumentParams(
                        TextDocumentIdentifier(virtualFile.toLanguageServerURL()),
                        virtualFile.readText()
                    )
                languageServer.textDocumentService.didSave(param)
            }
            VirtualFileManager.getInstance().asyncRefresh()
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    private fun shouldProcess(file: VirtualFile, index: ProjectFileIndex, project: Project): Boolean {
        var shouldProcess = false
        val application = ApplicationManager.getApplication()
        if (application.isDisposed) return false
        if (project.isDisposed) return false
        if (DumbService.getInstance(project).isDumb) shouldProcess = false

        application.runReadAction {
            val inCache = debounceFileCache.getIfPresent(file.path)
            if (inCache != null) {
                shouldProcess = false
            } else {
                debounceFileCache.put(file.path, true)
                if (index.isInContent(file) && !isInBlacklistedParentDir(file)) {
                    shouldProcess = true
                } else {
                    shouldProcess = false
                    return@runReadAction
                }
            }
        }
        return shouldProcess
    }

    private fun isInBlacklistedParentDir(file: VirtualFile): Boolean {
        val path = file.path.split(File.separatorChar)
            .filter { blackListedDirectories.contains(it.trimEnd(File.separatorChar)) }
        return path.isNotEmpty()
    }

    override fun forwardEvents(events: MutableList<out VFileEvent>) = Unit
}
