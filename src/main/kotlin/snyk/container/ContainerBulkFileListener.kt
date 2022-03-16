package snyk.container

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.findPsiFileIgnoringExceptions
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.isContainerEnabled

class ContainerBulkFileListener : SnykBulkFileListener() {

    private val log = logger<ContainerBulkFileListener>()

    override fun before(project: Project, events: List<VFileEvent>) {
        val virtualFilesDeletedOrMovedOrRenamed = getAffectedVirtualFiles(
            events,
            classesOfEventsToFilter = listOf(
                VFileDeleteEvent::class.java,
                VFileMoveEvent::class.java,
                VFilePropertyChangeEvent::class.java
            ),
            eventsFilter = { (it as? VFilePropertyChangeEvent)?.isRename != false }
        )
        if (virtualFilesDeletedOrMovedOrRenamed.isEmpty()) return
        // clean Container cached results for Container related deleted/moved/renamed files
        if (isContainerEnabled()) {
            val imageCache = getKubernetesImageCache(project)
            val kubernetesWorkloadFilesFromCache = imageCache?.getKubernetesWorkloadFilesFromCache() ?: emptySet()
            val containerRelatedVirtualFilesAffected = virtualFilesDeletedOrMovedOrRenamed.filter {
                kubernetesWorkloadFilesFromCache.contains(it)
            }
            imageCache?.cleanCache(virtualFilesDeletedOrMovedOrRenamed)
            updateContainerCache(containerRelatedVirtualFilesAffected, project)
        }
    }

    override fun after(project: Project, events: List<VFileEvent>) {
        val virtualFilesAffected = getAffectedVirtualFiles(
            events,
            eventToVirtualFileTransformer = { transformEventToNewVirtualFile(it) },
            classesOfEventsToFilter = listOf(
                VFileCreateEvent::class.java,
                VFileContentChangeEvent::class.java,
                VFileMoveEvent::class.java,
                VFileCopyEvent::class.java,
                VFilePropertyChangeEvent::class.java
            ),
            eventsFilter = { (it as? VFilePropertyChangeEvent)?.isRename != false }
        )
        if (virtualFilesAffected.isEmpty()) return

        // update Container cached results for Container related files
        if (isContainerEnabled()) {
            getKubernetesImageCache(project)?.updateCache(virtualFilesAffected)
            val containerRelatedVirtualFilesAffected = virtualFilesAffected.filter { virtualFile ->
                val knownContainerIssues: List<ContainerIssuesForImage> =
                    getSnykToolWindowPanel(project)?.currentContainerResult?.allCliIssues ?: emptyList()
                val containerFilesCached = knownContainerIssues
                    .flatMap { it.workloadImages }
                    .map { it.virtualFile }
                // if file was cached before - we should update cache even if it's none k8s file anymore
                if (containerFilesCached.contains(virtualFile)) return@filter true

                val psiFile = findPsiFileIgnoringExceptions(virtualFile, project) ?: return@filter false
                YAMLImageExtractor.isKubernetes(psiFile)
            }
            updateContainerCache(containerRelatedVirtualFilesAffected, project)
        }
    }

    private fun updateContainerCache(
        containerRelatedVirtualFilesAffected: List<VirtualFile>,
        project: Project
    ) {
        if (containerRelatedVirtualFilesAffected.isEmpty()) return
        log.debug("update Container cache for $containerRelatedVirtualFilesAffected")
        val toolWindowPanel = getSnykToolWindowPanel(project)
        val containerIssuesForImages = toolWindowPanel?.currentContainerResult?.allCliIssues ?: return

        val newContainerIssuesForImagesList = containerIssuesForImages.map { issuesForImage ->
            if (issuesForImage.workloadImages.any { containerRelatedVirtualFilesAffected.contains(it.virtualFile) }) {
                makeObsolete(issuesForImage)
            } else {
                issuesForImage
            }
        }

        val newContainerCache = ContainerResult(newContainerIssuesForImagesList, null)
        newContainerCache.rescanNeeded = true
        toolWindowPanel.currentContainerResult = newContainerCache
        ApplicationManager.getApplication().invokeLater {
            toolWindowPanel.displayContainerResults(newContainerCache)
        }
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    private fun makeObsolete(containerIssuesForImage: ContainerIssuesForImage): ContainerIssuesForImage =
        containerIssuesForImage.copy(
            vulnerabilities = containerIssuesForImage.vulnerabilities
                .map { elem -> elem.copy(obsolete = true) }
        )
}
