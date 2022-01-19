package snyk.container.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.getSnykToolWindowPanel
import snyk.container.ContainerIssuesForImage

class ContainerYamlAnnotator : ExternalAnnotator<PsiFile, Unit>() {
    private val logger = logger<ContainerYamlAnnotator>()

    // overrides needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile? = file
    override fun doAnnotate(collectedInfo: PsiFile?): Unit = Unit

    fun getContainerIssuesForImages(psiFile: PsiFile): List<ContainerIssuesForImage> {
        logger.debug("Calling doAnnotate on ${psiFile.name}")

        val containerResult = getSnykToolWindowPanel(psiFile.project)?.currentContainerResult

        ProgressManager.checkCanceled()

        return containerResult?.allCliIssues
            ?.filter { forImage -> forImage.workloadImages.find { it.psiFile == psiFile } != null }
            ?: emptyList()
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        val issuesForImages = getContainerIssuesForImages(psiFile)

        if (issuesForImages.isEmpty()) return

        issuesForImages.forEach { forImage ->
            if (forImage.ignored || forImage.obsolete) return

            val severity = severity(forImage)

            val workloadImage = forImage.workloadImages.first { it.psiFile == psiFile }
            val textRange = textRange(psiFile, workloadImage.lineNumber, forImage.imageName)
            val annotationBuilder = holder.newAnnotation(severity, annotationMessage(forImage)).range(textRange)
            if (shouldAddQuickFix(forImage)) {
                val intentionAction = BaseImageRemediationFix(forImage, textRange)
                annotationBuilder.withFix(intentionAction)
            }
            annotationBuilder.create()
        }
    }

    private fun shouldAddQuickFix(forImage: ContainerIssuesForImage): Boolean {
        val baseImageRemediation = forImage.docker.baseImageRemediation
        if (baseImageRemediation == null || !baseImageRemediation.isRemediationAvailable()) return false
        if (forImage.baseImageRemediationInfo == null) return false

        val baseImageName = forImage.imageName.split(":")[0]
        val imageNameToFix =
            BaseImageRemediationFix.determineTargetImage(forImage.baseImageRemediationInfo).split(":")[0]
        return baseImageName == imageNameToFix
    }

    fun severity(forImage: ContainerIssuesForImage): HighlightSeverity {
        val severities = forImage.vulnerabilities.groupBy { it.severity }
        return when {
            severities[SEVERITY_CRITICAL]?.isNotEmpty() == true -> HighlightSeverity.ERROR
            severities[SEVERITY_HIGH]?.isNotEmpty() == true -> HighlightSeverity.WARNING
            severities[SEVERITY_MEDIUM]?.isNotEmpty() == true -> HighlightSeverity.WEAK_WARNING
            severities[SEVERITY_LOW]?.isNotEmpty() == true -> HighlightSeverity.INFORMATION
            else -> HighlightSeverity.INFORMATION
        }
    }

    fun annotationMessage(image: ContainerIssuesForImage): String {
        val severities = image.vulnerabilities.distinctBy { it.id }
        val total = severities.size
        val vulnerabilityString = if (total == 1) "vulnerability" else "vulnerabilities"
        return buildString {
            val remediationString = when {
                image.baseImageRemediationInfo != null -> "Upgrade image to a newer version"
                else -> ""
            }
            append("Snyk found $total $vulnerabilityString. $remediationString")
        }
    }

    fun textRange(psiFile: PsiFile, line: Int, imageName: String): TextRange {
        val document = psiFile.viewProvider.document ?: return TextRange.EMPTY_RANGE
        val documentLine = line - 1
        val startOffset = document.getLineStartOffset(documentLine)
        val endOffset = document.getLineEndOffset(documentLine)
        val lineRange = TextRange.create(startOffset, endOffset)
        val text = document.getText(lineRange)
        val lineOffSet = startOffset + text.indexOf(imageName)
        return TextRange.create(lineOffSet, lineOffSet + imageName.length)
    }

    companion object {
        const val SEVERITY_CRITICAL = "critical"
        const val SEVERITY_HIGH = "high"
        const val SEVERITY_MEDIUM = "medium"
        const val SEVERITY_LOW = "low"
    }
}
