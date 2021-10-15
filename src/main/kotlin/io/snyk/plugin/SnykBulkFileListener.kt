package io.snyk.plugin

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.*
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import java.util.function.Predicate

private val log = logger<SnykBulkFileListener>()

class SnykBulkFileListener : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        updateCaches(
            events,
            listOf(
                VFileContentChangeEvent::class.java,
                VFileMoveEvent::class.java,
                VFileCopyEvent::class.java
            )
        )
    }

    override fun before(events: MutableList<out VFileEvent>) {
        cleanCaches(
            events,
            listOf(
                VFileContentChangeEvent::class.java,
                VFileMoveEvent::class.java,
                VFileCopyEvent::class.java,
                VFileDeleteEvent::class.java
            )
        )
    }

    private fun cleanCaches(events: List<out VFileEvent>, classesOfEventsToFilter: Collection<Class<*>>) {
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
                    log.debug("OSS cached results dropped due to changes in: $buildFileChanged")
                }
            }

            // clean IaC cached results if needed
            val currentIacResult = toolWindowPanel.currentIacResult
            if (currentIacResult != null) {
                val iacRelatedFileChanged = virtualFilesAffected
                    .filter { iacFileExtensions.contains(it.extension) }
                    .find { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }
                if (iacRelatedFileChanged != null) {
                    toolWindowPanel.currentIacResult = null
                    log.debug("IaC cached results dropped due to changes in: $iacRelatedFileChanged")
                }
            }

            // if SnykCode analysis is running then re-run it (with updated files)
            val manager = PsiManager.getInstance(project)
            val supportedFileChanged = virtualFilesAffected
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

    private fun updateCaches(events: List<out VFileEvent>, classesOfEventsToFilter: Collection<Class<out VFileEvent>>) {
        for (project in ProjectUtil.getOpenProjects()) {
            if (project.isDisposed) continue

            val virtualFilesAffected = getAffectedVirtualFiles(
                events,
                fileFilter = Predicate { true },
                classesOfEventsToFilter = classesOfEventsToFilter
            )

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
