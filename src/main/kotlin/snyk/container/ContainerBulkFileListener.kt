package snyk.container

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.*

class ContainerBulkFileListener(project: Project) : SnykBulkFileListener(project) {

    private val log = logger<ContainerBulkFileListener>()

    override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        if (virtualFilesAffected.isEmpty()) return
        // clean Container cached results for Container related deleted/moved/renamed files
        val imageCache = getKubernetesImageCache(project)
        val kubernetesWorkloadFilesFromCache = imageCache?.getKubernetesWorkloadFilesFromCache() ?: emptySet()
        val containerRelatedVirtualFilesAffected = virtualFilesAffected.filter {
            kubernetesWorkloadFilesFromCache.contains(it)
        }
        imageCache?.cleanCache(virtualFilesAffected)
        updateContainerCache(containerRelatedVirtualFilesAffected, project)
    }

    override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        if (virtualFilesAffected.isEmpty()) return
        // update Container cached results for Container related files
        getKubernetesImageCache(project)?.updateCache(virtualFilesAffected)
        val containerRelatedVirtualFilesAffected = virtualFilesAffected.filter { virtualFile ->
            val knownContainerIssues: List<ContainerIssuesForImage> =
                getSnykCachedResults(project)?.currentContainerResult?.allCliIssues ?: emptyList()
            val containerFilesCached = knownContainerIssues
                .flatMap { it.workloadImages }
                .map { it.virtualFile }
            // if file was cached before - we should update cache even if it's none k8s file anymore
            if (containerFilesCached.contains(virtualFile) || isDotSnykFile(virtualFile)) return@filter true

            val psiFile = findPsiFileIgnoringExceptions(virtualFile, project) ?: return@filter false
            YAMLImageExtractor.isKubernetes(psiFile)
        }
        updateContainerCache(containerRelatedVirtualFilesAffected, project)
    }

    private fun isDotSnykFile(virtualFile: VirtualFile) = virtualFile.name.endsWith(".snyk")

    private fun updateContainerCache(
        containerRelatedVirtualFilesAffected: List<VirtualFile>,
        project: Project
    ) {
        if (containerRelatedVirtualFilesAffected.isEmpty()) return
        log.debug("update Container cache for $containerRelatedVirtualFilesAffected")

        val isDotSnyk = containerRelatedVirtualFilesAffected.any(::isDotSnykFile)

        val snykCachedResults = getSnykCachedResults(project)
        val currentContainerResult = snykCachedResults?.currentContainerResult ?: return
        val containerIssuesForImages = currentContainerResult.allCliIssues ?: return

        val newContainerIssuesForImagesList = containerIssuesForImages.map { issuesForImage ->
            if (issuesForImage.workloadImages.any { containerRelatedVirtualFilesAffected.contains(it.virtualFile) } || isDotSnyk) {
                makeObsolete(issuesForImage)
            } else {
                issuesForImage
            }


        }

        val newContainerCache = ContainerResult(newContainerIssuesForImagesList, currentContainerResult.errors)
        newContainerCache.rescanNeeded = true
        snykCachedResults.currentContainerResult = newContainerCache
        ApplicationManager.getApplication().invokeLater {
            getSnykToolWindowPanel(project)?.displayContainerResults(newContainerCache)
        }
        refreshAnnotationsForOpenFiles(project)
    }

    private fun makeObsolete(containerIssuesForImage: ContainerIssuesForImage): ContainerIssuesForImage =
        containerIssuesForImage.copy(
            vulnerabilities = containerIssuesForImage.vulnerabilities
                .map { elem -> elem.copy(obsolete = true) }
        )
}
