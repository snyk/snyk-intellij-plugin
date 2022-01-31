package io.snyk.plugin

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.PsiFile
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import okhttp3.internal.toImmutableList
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult
import java.util.function.Predicate

private val LOG = logger<SnykBulkFileListener>()

/**
 * For our caches we need following events for file: Create, Change, Delete
 * Also in our caches we need next type of actions: _add_ _update_ _clean_
 * Note: Current implementation meaning:
 *  add - mark results as invalid anymore (i.e. next scan attempt should run scan and not use cache)
 *  update - mark item(file) result as obsolete (i.e. next scan attempt should run scan and not use cache)
 *  clean - mark item(file) result as obsolete (i.e. next scan attempt should run scan and not use cache)
 *
 * BulkFileListener provide next events: Create, ContentChange, Move, Copy, Delete
 * Also in `before` state we have access to old files.
 * While in `after` state we have access for new/updated files too (but old might not exist anymore)
 *
 * Next mapping/interpretation for BulkFileListener type of events should be used:
 * Create
 *  - addressed at `after` state, new file processed to _add_ caches
 * ContentChange
 *  - addressed at `after` state, new file processed to _update_ caches
 * Move
 *  - addressed at `before` state, old file processed to _clean_ caches
 *  - addressed at `after` state, new file processed to _add_ caches
 * Copy
 *  - addressed at `after` state, new file processed to _add_ caches
 * Delete
 *  - addressed at `before` state, old file processed to _clean_ caches
 *
 */
class SnykBulkFileListener : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        if (isFileListenerEnabled()) {
            updateCaches(
                events,
                listOf(
                    VFileCreateEvent::class.java,
                    VFileContentChangeEvent::class.java,
                    VFileMoveEvent::class.java,
                    VFileCopyEvent::class.java
                )
            )

            if (isContainerEnabled()) {
                for (project in ProjectUtil.getOpenProjects()) {
                    getKubernetesImageCache(project)?.extractFromEvents(events)
                }
            }
        }
    }

    override fun before(events: MutableList<out VFileEvent>) {
        if (isFileListenerEnabled()) {
            cleanCaches(
                events,
                listOf(
                    VFileCreateEvent::class.java,
                    VFileContentChangeEvent::class.java,
                    VFileMoveEvent::class.java,
                    VFileCopyEvent::class.java,
                    VFileDeleteEvent::class.java
                )
            )
        }
    }

    private fun cleanCaches(events: List<VFileEvent>, classesOfEventsToFilter: Collection<Class<*>>) {
        for (project in ProjectUtil.getOpenProjects()) {
            if (project.isDisposed) continue

            val virtualFilesAffected = getAffectedVirtualFiles(
                events,
                classesOfEventsToFilter = classesOfEventsToFilter
            )

            val toolWindowPanel = getSnykToolWindowPanel(project)

            // clean OSS cached results if needed
            if (toolWindowPanel?.currentOssResults != null) {
                val buildFileChanged = virtualFilesAffected
                    .filter { supportedBuildFiles.contains(it.name) }
                    .find { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }
                if (buildFileChanged != null) {
                    toolWindowPanel.currentOssResults = null
                    LOG.debug("OSS cached results dropped due to changes in: $buildFileChanged")
                }
            }

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

            // clean IaC cached results for deleted/moved files
            updateIacCache(
                getAffectedVirtualFiles(
                    events,
                    classesOfEventsToFilter = listOf(VFileDeleteEvent::class.java, VFileMoveEvent::class.java)
                ),
                project
            )

            // todo: clean Container cached results for deleted/moved files
        }
    }

    private fun updateIacCache(
        virtualFilesAffected: Set<VirtualFile>,
        project: Project
    ) {
        if (virtualFilesAffected.isEmpty()) return
        val toolWindowPanel = getSnykToolWindowPanel(project)
        val iacFiles = toolWindowPanel?.currentIacResult?.allCliIssues ?: return

        var changed = false

        val newIacFileList = iacFiles.toMutableList()
        virtualFilesAffected
            .filter { iacFileExtensions.contains(it.extension) }
            .filter { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }
            .forEach {
                changed = true // for new files we need to "dirty" the cache, too
                newIacFileList.forEachIndexed { i, iacIssuesForFile ->
                    if (pathsEqual(it.path, iacIssuesForFile.targetFilePath)) {
                        val obsoleteIacFile = makeObsolete(iacIssuesForFile)
                        newIacFileList[i] = obsoleteIacFile
                    }
                }
            }

        if (changed) {
            val newIacCache = IacResult(newIacFileList.toImmutableList(), null)
            newIacCache.iacScanNeeded = true
            toolWindowPanel.currentIacResult = newIacCache
            ApplicationManager.getApplication().invokeLater {
                toolWindowPanel.displayIacResults(newIacCache)
            }
            DaemonCodeAnalyzer.getInstance(toolWindowPanel.project).restart()
        }
    }

    private fun makeObsolete(iacIssuesForFile: IacIssuesForFile): IacIssuesForFile {
        val obsoleteIssueList = iacIssuesForFile.infrastructureAsCodeIssues
            .map { elem -> elem.copy(obsolete = true) }.toList()
        return iacIssuesForFile.copy(infrastructureAsCodeIssues = obsoleteIssueList, obsolete = true)
    }

    private fun updateCaches(events: List<VFileEvent>, classesOfEventsToFilter: Collection<Class<out VFileEvent>>) {
        for (project in ProjectUtil.getOpenProjects()) {
            if (project.isDisposed) continue

            val virtualFilesAffected = getAffectedVirtualFiles(
                events,
                eventToVirtualFileTransformer = {
                    when (it) {
                        is VFileCopyEvent -> it.findCreatedFile()
                        is VFileMoveEvent -> if (it.newParent.isValid) it.newParent.findChild(it.file.name) else null
                        else -> it.file
                    }
                },
                classesOfEventsToFilter = classesOfEventsToFilter
            )

            // update IaC cached results if needed
            updateIacCache(virtualFilesAffected, project)

            // update .dcignore caches if needed
            SnykCodeIgnoreInfoHolder.instance.updateIgnoreFileCachesIfAffected(project, virtualFilesAffected)

            //todo: update Container cached results if needed
        }
    }

    private fun getAffectedVirtualFiles(
        events: List<VFileEvent>,
        eventToVirtualFileTransformer: (VFileEvent) -> VirtualFile? = { it.file },
        fileFilter: Predicate<VirtualFile> = Predicate { true },
        classesOfEventsToFilter: Collection<Class<*>>
    ): Set<VirtualFile> {
        return events.asSequence()
            .filter { event -> instanceOf(event, classesOfEventsToFilter) }
            .mapNotNull(eventToVirtualFileTransformer)
            .filter(fileFilter::test)
            .toSet()
    }

    private fun instanceOf(obj: Any, classes: Collection<Class<*>>): Boolean {
        for (c in classes) {
            if (c.isInstance(obj)) return true
        }
        return false
    }

    companion object {
        // see https://github.com/snyk/snyk/blob/master/src/lib/detect.ts#L10
        private val supportedBuildFiles = listOf(
            "yarn.lock",
            "package-lock.json",
            "package.json",
            "Gemfile",
            "Gemfile.lock",
            "pom.xml",
            "build.gradle",
            "build.gradle.kts",
            "build.sbt",
            "Pipfile",
            "requirements.txt",
            "Gopkg.lock",
            "go.mod",
            "vendor.json",
            "project.assets.json",
            "project.assets.json",
            "packages.config",
            "paket.dependencies",
            "composer.lock",
            "Podfile",
            "Podfile.lock",
            "pyproject.toml",
            "poetry.lock"
        )

        // see https://github.com/snyk/snyk/blob/master/src/lib/iac/constants.ts#L7
        private val iacFileExtensions = listOf(
            "yaml",
            "yml",
            "json",
            "tf"
        )
    }
}
