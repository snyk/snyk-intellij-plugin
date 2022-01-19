package snyk.container.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.getSnykToolWindowPanel
import org.jetbrains.annotations.TestOnly
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
            holder.newAnnotation(severity, annotationMessage(forImage))
                .range(textRange(psiFile, workloadImage.lineNumber, forImage.imageName))
                .create()
        }
    }

    @TestOnly
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
        val severities = image.vulnerabilities.distinctBy { it.id }.groupBy { it.severity }
        val criticalCount = severities[SEVERITY_CRITICAL]?.size ?: 0
        val highCount = severities[SEVERITY_HIGH]?.size ?: 0
        val mediumCount = severities[SEVERITY_MEDIUM]?.size ?: 0
        val lowCount = severities[SEVERITY_LOW]?.size ?: 0
        return buildString {
            val remediationString = when {
                image.baseImageRemediationInfo != null -> " Remediation available."
                else -> ""
            }
            append(
                "Snyk - Vulnerabilities found. " +
                    "Critical: $criticalCount, " +
                    "High: $highCount, " +
                    "Medium: $mediumCount, " +
                    "Low: $lowCount." +
                    remediationString
            )
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
