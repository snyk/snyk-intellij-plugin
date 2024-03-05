package io.snyk.plugin.snykcode

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.readText
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.getPsiFile
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.PDU
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import snyk.common.lsp.LanguageServerWrapper

class SnykCodeBulkFileListener : SnykBulkFileListener() {
    override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        val filesAffected = toSnykCodeFileSet(
            project,
            virtualFilesAffected
        )
        if (isSnykCodeLSEnabled()) {
            return
        }

        // if SnykCode analysis is running then re-run it (with updated files)
        val supportedFileChanged = filesAffected
            .filter { it.virtualFile.isValid }
            .any { SnykCodeUtils.instance.isSupportedFileFormat(it) }
        val isSnykCodeRunning = AnalysisData.instance.isUpdateAnalysisInProgress(project)
        if (supportedFileChanged && isSnykCodeRunning) {
            RunUtils.instance.rescanInBackgroundCancellableDelayed(project, 0, false, false)
        }
        // remove changed files from SnykCode cache
        val allCachedFiles = AnalysisData.instance.getAllCachedFiles(project)
        val filesToRemoveFromCache = allCachedFiles
            .filter { cachedFile ->
                val snykCodeFile = PDU.toSnykCodeFile(cachedFile)
                snykCodeFile in filesAffected ||
                    // Directory containing cached file is deleted/moved
                    filesAffected.any {
                        it.virtualFile.isDirectory && snykCodeFile.virtualFile.path.startsWith(it.virtualFile.path)
                    }
            }
        if (filesToRemoveFromCache.isNotEmpty()) {
            getSnykTaskQueueService(project)?.scheduleRunnable("Snyk Code is updating caches...") {
                if (filesToRemoveFromCache.size > 10) {
                    // bulk files change event - better to drop cache and perform full rescan later
                    AnalysisData.instance.removeProjectFromCaches(project)
                } else {
                    AnalysisData.instance.removeFilesFromCache(filesToRemoveFromCache)
                }
            }
        }
        // clean .dcignore caches if needed
        SnykCodeIgnoreInfoHolder.instance.cleanIgnoreFileCachesIfAffected(filesAffected)
    }

    override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        val filesAffected = toSnykCodeFileSet(project, virtualFilesAffected)

        if (isSnykCodeLSEnabled()) {
            updateCacheAndUI(filesAffected, project)
            return
        }
        /* update .dcignore caches if needed */
        SnykCodeIgnoreInfoHolder.instance.updateIgnoreFileCachesIfAffected(filesAffected)
    }

    override fun forwardEvents(events: MutableList<out VFileEvent>) {
        if (!isSnykCodeLSEnabled()) return
        LanguageServerWrapper.getInstance().ensureLanguageServerInitialized()
        val languageServer = LanguageServerWrapper.getInstance().languageServer
        for (event in events) {
            if (event.file == null || !event.isFromSave) continue
            val file = event.file!!
            val activeProject = ProjectUtil.getActiveProject()
            ProgressManager.getInstance().run(object : Backgroundable(
                activeProject,
                "Snyk: forwarding save event to Language Server"
            ) {
                override fun run(indicator: ProgressIndicator) {
                    val param = DidSaveTextDocumentParams(TextDocumentIdentifier(file.url), file.readText())
                    languageServer.textDocumentService.didSave(param)
                }
            })
        }
    }

    private fun updateCacheAndUI(filesAffected: Set<SnykCodeFile>, project: Project) {
        val cache = getSnykCachedResults(project)?.currentSnykCodeResultsLS ?: return
        filesAffected.forEach {
            cache.remove(it)
            it.virtualFile.getPsiFile(project)?.let { psiFile ->
                DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
            }
        }
    }

    private fun toSnykCodeFileSet(project: Project, virtualFiles: Set<VirtualFile>) =
        virtualFiles.map { SnykCodeFile(project, it) }.toSet()
}
