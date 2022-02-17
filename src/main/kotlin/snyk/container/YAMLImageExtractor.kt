package snyk.container

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.snyk.plugin.findPsiFileIgnoringExceptions

object YAMLImageExtractor {
    private val logger = Logger.getInstance(javaClass.name)
    private val imageRegEx = "(?<=image:)(\\s*[a-zA-Z0-9./\\-_]+)(:)?([a-zA-Z0-9./\\-_]+)?".toRegex()

    private fun extractImages(file: PsiFile): Set<KubernetesWorkloadImage> {
        return if (!isKubernetes(file)) emptySet() else extractImagesFromText(file)
    }

    private fun extractImagesFromText(psiFile: PsiFile): Set<KubernetesWorkloadImage> {
        val extractedImages = mutableListOf<KubernetesWorkloadImage>()
        getFileLines(psiFile).forEachIndexed { lineNumber, line ->
            val imageName = extractImageNameFromLine(line)
            if (imageName.isNotBlank()) {
                // we report line numbers with a start index of 1 elsewhere (e.g. IaC)
                val image = KubernetesWorkloadImage(imageName, psiFile.virtualFile, lineNumber + 1)
                extractedImages.add(image)
                logger.debug("Found image $image")
            }
        }
        return extractedImages.toSet()
    }

    private fun extractImageNameFromLine(s: String): String {
        return if (!s.trim().startsWith("#")) {
            imageRegEx.find(s)?.value?.trim() ?: ""
        } else {
            ""
        }
    }

    fun extractFromFile(file: VirtualFile, project: Project): Set<KubernetesWorkloadImage> {
        val psiFile = findPsiFileIgnoringExceptions(file, project)
        if (psiFile == null || file.isDirectory || !file.isValid) {
            logger.warn("no psi file found for ${file.path}")
            return emptySet()
        }

        return extractImages(psiFile)
    }

    private fun getFileLines(psiFile: PsiFile) =
        psiFile.viewProvider.document?.text?.lines() ?: emptyList()

    fun isKubernetes(psiFile: PsiFile): Boolean =
        isKubernetesFileExtension(psiFile) && isKubernetesFileContent(getFileLines(psiFile))

    private fun isKubernetesFileExtension(psiFile: PsiFile): Boolean =
        psiFile.virtualFile.extension == "yaml" || psiFile.virtualFile.extension == "yml"

    internal fun isKubernetesFileContent(lines: List<String>): Boolean {
        val normalizedLines =
            lines
                .map { line -> line.trim() }
                .filter { line -> line.isNotBlank() }
                .filter { line -> !line.startsWith("#") }
                .filter { line -> !line.startsWith("---") }

        if (normalizedLines.size > 1) {
            return normalizedLines[0].startsWith("apiVersion:") &&
                normalizedLines[1].startsWith("kind:")
        }
        return false
    }
}
