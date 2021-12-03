package snyk.container

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.stream.Collectors
import kotlin.streams.toList

object YAMLImageExtractor {
    private val logger = Logger.getInstance(javaClass.name)

    private fun extractImages(file: PsiFile): List<String> {
        return if (!isKubernetes(file)) emptyList() else extractImagesFromText(file)
    }

    private fun extractImagesFromText(psiFile: PsiFile) =
        getFileLines(psiFile).stream()
            .filter { s -> s.trim().startsWith("image:") }
            .map { s ->
                s.trim()
                    .split(":")
                    .stream().filter { e -> e.trim() != "image" }
                    .map { image -> image.trim() }
                    .collect(Collectors.joining(":"))
            }.toList()

    fun extractFromFile(file: VirtualFile, project: Project): Set<KubernetesWorkloadImage> {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (psiFile == null || file.isDirectory || !file.isValid) return emptySet()

        return extractImages(psiFile)
            .map { image ->
                logger.debug("added $image to cache.")
                KubernetesWorkloadImage(image, psiFile)
            }.toSet()
    }

    private fun getFileLines(psiFile: PsiFile) =
        psiFile.viewProvider.document?.text?.split("\n") ?: emptyList()

    private fun isKubernetes(psiFile: PsiFile): Boolean = isKubernetes(getFileLines(psiFile))

    internal fun isKubernetes(lines: List<String>): Boolean {
        val normalizedLines =
            lines
                .map { line -> line.trim() }
                .filter { line -> line.isNotBlank() }
                .filter { line -> !line.startsWith("#") }

        if (normalizedLines.size > 1) {
            return normalizedLines[0].startsWith("apiVersion:") &&
                normalizedLines[1].startsWith("kind:")
        }
        return false
    }
}
