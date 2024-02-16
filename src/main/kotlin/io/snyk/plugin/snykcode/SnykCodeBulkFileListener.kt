package io.snyk.plugin.snykcode

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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

class SnykCodeBulkFileListener : SnykBulkFileListener() {

    private val logger = logger<SnykCodeBulkFileListener>()

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
            processAfterForLS(filesAffected, project)
            return
        }
        /* update .dcignore caches if needed */
        SnykCodeIgnoreInfoHolder.instance.updateIgnoreFileCachesIfAffected(filesAffected)
    }

    private fun processAfterForLS(filesAffected: Set<SnykCodeFile>, project: Project) {
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
