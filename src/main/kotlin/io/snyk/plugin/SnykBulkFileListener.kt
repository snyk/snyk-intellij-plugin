package io.snyk.plugin

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbService
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
import com.intellij.psi.PsiManager
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import okhttp3.internal.toImmutableList
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult
import java.util.function.Predicate

private val LOG = logger<SnykBulkFileListener>()

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
                    getKubernetesImageCache(project).extractFromEvents(events)
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
                fileFilter = Predicate { true },
                classesOfEventsToFilter = classesOfEventsToFilter
            )

            val toolWindowPanel = project.service<SnykToolWindowPanel>()

            // clean OSS cached results if needed
            if (toolWindowPanel.currentOssResults != null) {
                val buildFileChanged = virtualFilesAffected
                    .filter { supportedBuildFiles.contains(it.name) }
                    .find { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }
                if (buildFileChanged != null) {
                    toolWindowPanel.currentOssResults = null
                    LOG.debug("OSS cached results dropped due to changes in: $buildFileChanged")
                }
            }

            // if SnykCode analysis is running then re-run it (with updated files)
            val manager = PsiManager.getInstance(project)
            val supportedFileChanged = virtualFilesAffected
                .filter { it.isValid }
                .mapNotNull { manager.findFile(it) }
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

            if (filesToRemoveFromCache.isNotEmpty())
                project.service<SnykTaskQueueService>().scheduleRunnable("Snyk Code is updating caches...") {
                    if (filesToRemoveFromCache.size > 10) {
                        // bulk files change event (like `git checkout`) - better to drop cache and perform full rescan later
                        AnalysisData.instance.removeProjectFromCaches(project)
                    } else {
                        AnalysisData.instance.removeFilesFromCache(filesToRemoveFromCache)
                    }
                }

            // clean .dcignore caches if needed
            SnykCodeIgnoreInfoHolder.instance.cleanIgnoreFileCachesIfAffected(project, virtualFilesAffected)
        }
    }

    private fun updateIacCache(
        virtualFilesAffected: Set<VirtualFile>,
        toolWindowPanel: SnykToolWindowPanel
    ) {
        val iacCache = toolWindowPanel.currentIacResult
        val iacFiles = iacCache?.allCliIssues ?: return
        val project = toolWindowPanel.project

        runBackgroundableTask("Updating Snyk Infrastructure As Code Cache...", project, true) {
            DumbService.getInstance(project).runReadActionInSmartMode {
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
                    toolWindowPanel.currentIacResult = newIacCache
                    toolWindowPanel.displayIacResults(newIacCache)
                    toolWindowPanel.iacScanNeeded = true
                    DaemonCodeAnalyzer.getInstance(toolWindowPanel.project).restart()
                }
            }
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
                fileFilter = Predicate { true },
                classesOfEventsToFilter = classesOfEventsToFilter
            )

            // update IaC cached results if needed
            val toolWindowPanel = project.service<SnykToolWindowPanel>()
            val allCliIssues = toolWindowPanel.currentIacResult?.allCliIssues
            if (allCliIssues != null && allCliIssues.isNotEmpty()) {
                updateIacCache(virtualFilesAffected, toolWindowPanel)
            }

            // update .dcignore caches if needed
            SnykCodeIgnoreInfoHolder.instance.updateIgnoreFileCachesIfAffected(project, virtualFilesAffected)
        }
    }

    private fun getAffectedVirtualFiles(
        events: List<VFileEvent>,
        fileFilter: Predicate<VirtualFile>,
        classesOfEventsToFilter: Collection<Class<*>>
    ): Set<VirtualFile> {
        return events.asSequence()
            .filter { event -> instanceOf(event, classesOfEventsToFilter) }
            .mapNotNull(VFileEvent::getFile)
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
