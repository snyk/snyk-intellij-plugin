package snyk.container

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.NonUrgentExecutor
import com.jetbrains.rd.util.concurrentMapOf
import java.util.concurrent.Callable

@Service
class KubernetesImageCache(val project: Project) {
    private val images = concurrentMapOf<VirtualFile, Set<KubernetesWorkloadImage>>()
    private val logger = Logger.getInstance(javaClass)

    fun clear() {
        images.clear()
    }

    fun cacheKubernetesFileFromProject() {
        val callable = Callable {
            ProjectRootManager.getInstance(project).fileIndex.iterateContent { virtualFile ->
                extractFromFileAndAddToCache(virtualFile)
                true
            }
        }
        ReadAction.nonBlocking(callable).submit(NonUrgentExecutor.getInstance())
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
            extractFromFileAndAddToCache(file)
        }
    }

    /** public for Tests only */
    fun extractFromFileAndAddToCache(file: VirtualFile) {
        val extractFromFile = YAMLImageExtractor.extractFromFile(file, project)
        if (extractFromFile.isNotEmpty()) {
            logger.debug("${if (images.contains(file)) "updated" else "added"} $file in cache")
            images[file] = extractFromFile
        } else if (images.contains(file)) {
            // case when all images from file (cached before) been removed
            logger.debug("removed $file from cache")
            images.remove(file)
        }
    }
}
