package io.snyk.plugin.snykcode

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.PsiFile
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.findPsiFileIgnoringExceptions
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import io.snyk.plugin.snykcode.core.SnykCodeUtils

class SnykCodeBulkFileListener : SnykBulkFileListener() {

    private val log = logger<SnykCodeBulkFileListener>()

    override fun before(project: Project, events: List<VFileEvent>) {
        val virtualFilesAffected = getAffectedVirtualFiles(
            events,
            classesOfEventsToFilter = listOf(
                VFileCreateEvent::class.java,
                VFileContentChangeEvent::class.java,
                VFileMoveEvent::class.java,
                VFileCopyEvent::class.java,
                VFileDeleteEvent::class.java
            )
        )
        // if SnykCode analysis is running then re-run it (with updated files)
        val supportedFileChanged = virtualFilesAffected
            .filter { it.isValid }
            .mapNotNull { findPsiFileIgnoringExceptions(it, project) }
            .any { SnykCodeUtils.instance.isSupportedFileFormat(it) }
        val isSnykCodeRunning = AnalysisData.instance.isUpdateAnalysisInProgress(project)
        if (supportedFileChanged && isSnykCodeRunning) {
            RunUtils.instance.rescanInBackgroundCancellableDelayed(project, 0, false, false)
        }
        // remove changed files from SnykCode cache
        val allCachedFiles = AnalysisData.instance.getAllCachedFiles(project)
        val filesToRemoveFromCache = allCachedFiles
            .filter { cachedPsiFile ->
                val vFile = (cachedPsiFile as PsiFile).virtualFile
                vFile in virtualFilesAffected ||
                    // Directory containing cached file is deleted/moved
                    virtualFilesAffected.any { it.isDirectory && vFile.path.startsWith(it.path) }
            }
        if (filesToRemoveFromCache.isNotEmpty()) {
            getSnykTaskQueueService(project)?.scheduleRunnable("Snyk Code is updating caches...") {
                if (filesToRemoveFromCache.size > 10) {
                    // bulk files change event (like `git checkout`) - better to drop cache and perform full rescan later
                    AnalysisData.instance.removeProjectFromCaches(project)
                } else {
                    AnalysisData.instance.removeFilesFromCache(filesToRemoveFromCache)
                }
            }
        }
        // clean .dcignore caches if needed
        SnykCodeIgnoreInfoHolder.instance.cleanIgnoreFileCachesIfAffected(project, virtualFilesAffected)
    }

    override fun after(project: Project, events: List<VFileEvent>) {
        val virtualFilesAffected = getAffectedVirtualFiles(
            events,
            eventToVirtualFileTransformer = { transformEventToNewVirtualFile(it) },
            classesOfEventsToFilter = listOf(
                VFileCreateEvent::class.java,
                VFileContentChangeEvent::class.java,
                VFileMoveEvent::class.java,
                VFileCopyEvent::class.java
            )
        )
        // update .dcignore caches if needed
        SnykCodeIgnoreInfoHolder.instance.updateIgnoreFileCachesIfAffected(project, virtualFilesAffected)
    }
}
