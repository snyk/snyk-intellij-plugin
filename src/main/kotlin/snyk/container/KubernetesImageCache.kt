package snyk.container

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.util.concurrentMapOf

@Service
class KubernetesImageCache(val project: Project) {
    private val images = concurrentMapOf<VirtualFile, Set<KubernetesWorkloadImage>>()
    private val logger = Logger.getInstance(javaClass.name)

    fun clear() {
        images.clear()
    }

    fun scanProjectForKubernetesFiles() {
        val title = "Snyk: Scanning For Kubernetes Files..."
        runBackgroundableTask(title, project, true) { progress ->
            DumbService.getInstance(project).runReadActionInSmartMode {
                ProjectRootManager.getInstance(project).fileIndex.iterateContent { virtualFile ->
                    this.extractFromFile(virtualFile)
                    !progress.isCanceled
                }
            }
        }
    }

    fun getKubernetesWorkloadFilesFromCache(): Set<VirtualFile> = images.keys

    fun getKubernetesWorkloadImages(): Set<KubernetesWorkloadImage> = images.values.flatten().toSet()

    fun getKubernetesWorkloadImageNamesFromCache(): Set<String> =
        getKubernetesWorkloadImages().map { v -> v.image }.toSet()

    fun cleanCache(files: Set<VirtualFile>) {
        files.forEach { file ->
            images.remove(file)
            logger.debug("removed $file from cache")
        }
    }

    fun updateCache(files: Set<VirtualFile>) {
        files.forEach { file ->
            extractFromFile(file)
        }
    }

    /** public for Tests only */
    fun extractFromFile(file: VirtualFile) {
        val extractFromFile = YAMLImageExtractor.extractFromFile(file, project)
        if (extractFromFile.isNotEmpty()) {
            images[file] = extractFromFile
        }
    }
}
