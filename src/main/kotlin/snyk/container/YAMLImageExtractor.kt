package snyk.container

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.stream.Collectors

object YAMLImageExtractor {
    private val logger = Logger.getInstance(javaClass.name)

    private fun extractImages(file: PsiFile): Set<KubernetesWorkloadImage> {
        return if (!isKubernetes(file)) emptySet() else extractImagesFromText(file)
    }

    private fun extractImagesFromText(psiFile: PsiFile): Set<KubernetesWorkloadImage> {
        val extractedImages = mutableListOf<KubernetesWorkloadImage>()
        getFileLines(psiFile).forEachIndexed { lineNumber, line ->
            if (line.trim().startsWith("image:")) {
                val imageName = extractImageNameFromLine(line)
                // we report line numbers with a start index of 1 elsewhere (e.g. IaC)
                val image = KubernetesWorkloadImage(imageName, psiFile, lineNumber + 1)
                extractedImages.add(image)
                logger.debug("Found image $image")
            }
        }
        return extractedImages.toSet()
    }

    private fun extractImageNameFromLine(s: String): String {
        return s.trim()
            .split(":")
            .stream().filter { e -> e.trim() != "image" }
            .map { image -> image.trim() }
            .collect(Collectors.joining(":"))
    }

    fun extractFromFile(file: VirtualFile, project: Project): Set<KubernetesWorkloadImage> {
        val psiFile = PsiManager.getInstance(project).findFile(file)
        if (psiFile == null || file.isDirectory || !file.isValid) return emptySet()

        return extractImages(psiFile)
    }

    private fun getFileLines(psiFile: PsiFile) =
        psiFile.viewProvider.document?.text?.lines() ?: emptyList()

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
