package snyk.container.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import snyk.common.AnnotatorCommon
import snyk.common.intentionactions.ShowDetailsIntentionActionBase
import snyk.container.ContainerIssuesForImage

class ContainerYamlAnnotator : ExternalAnnotator<PsiFile, Unit>() {
    private val logger = logger<ContainerYamlAnnotator>()

    // override needed for the Annotator to invoke apply(). We don't do anything here
    override fun collectInformation(file: PsiFile): PsiFile? = file

    // save all changes on disk to update caches through SnykBulkFileListener
    override fun doAnnotate(psiFile: PsiFile?) {
        AnnotatorCommon.prepareAnnotate(psiFile)
    }

    fun getContainerIssuesForImages(psiFile: PsiFile): List<ContainerIssuesForImage> {
        val containerResult = getSnykCachedResults(psiFile.project)?.currentContainerResult
        ProgressManager.checkCanceled()
        return containerResult?.allCliIssues
            ?.filter { forImage -> forImage.workloadImages.find { it.virtualFile == psiFile.virtualFile } != null }
            ?: emptyList()
    }

    override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        logger.debug("apply on ${psiFile.name}")
        getContainerIssuesForImages(psiFile)
            .filter { forImage ->
                !forImage.ignored && !forImage.obsolete &&
                    forImage.getSeverities().any { AnnotatorCommon.isSeverityToShow(it) }
            }
            .forEach { forImage ->
                val severityToShow = forImage.getSeverities()
                    .filter { AnnotatorCommon.isSeverityToShow(it) }
                    .max() ?: Severity.UNKNOWN
                val annotationMessage = annotationMessage(forImage)
                forImage.workloadImages
                    .filter { it.virtualFile == psiFile.virtualFile }
                    .forEach { workloadImage ->
                        val textRange = textRange(psiFile, workloadImage.lineNumber, forImage.imageName)
                        val annotationBuilder =
                            holder
                                .newAnnotation(severityToShow.getHighlightSeverity(), annotationMessage)
                                .range(textRange)
                        if (shouldAddQuickFix(forImage)) {
                            val intentionAction = BaseImageRemediationFix(forImage, textRange)
                            annotationBuilder.withFix(intentionAction)
                        }
                        annotationBuilder.withFix(
                            ShowDetailsIntentionAction(annotationMessage, forImage, severityToShow)
                        )
                        annotationBuilder.create()
                    }
            }
    }

    private fun shouldAddQuickFix(forImage: ContainerIssuesForImage): Boolean {
        if (forImage.baseImageRemediationInfo?.isRemediationAvailable() != true) return false

        val baseImageName = forImage.imageName.split(":")[0]
        val imageNameToFix =
            BaseImageRemediationFix.determineTargetImage(forImage.baseImageRemediationInfo).split(":")[0]
        return baseImageName == imageNameToFix
    }

    fun annotationMessage(image: ContainerIssuesForImage): String {
        val severities = image.vulnerabilities.distinctBy { it.id }
        val total = severities.size
        val vulnerabilityString = if (total == 1) "vulnerability" else "vulnerabilities"
        return buildString {
            val remediationString = when {
                image.baseImageRemediationInfo?.isRemediationAvailable() == true -> "Upgrade image to a newer version"
                else -> ""
            }
            append("Snyk found $total $vulnerabilityString. $remediationString")
        }
    }

    fun textRange(psiFile: PsiFile, line: Int, imageName: String): TextRange {
        val document = psiFile.viewProvider.document ?: return TextRange.EMPTY_RANGE
        val documentLine = line - 1
        if (documentLine < 0 || document.lineCount <= documentLine) return TextRange.EMPTY_RANGE
        val startOffset = document.getLineStartOffset(documentLine)
        val endOffset = document.getLineEndOffset(documentLine)
        val lineRange = TextRange.create(startOffset, endOffset)
        val text = document.getText(lineRange)
        val colStart = text.indexOf(imageName)
        if (colStart == -1) return TextRange.EMPTY_RANGE
        val lineOffSet = startOffset + colStart
        return TextRange.create(lineOffSet, lineOffSet + imageName.length)
    }

    inner class ShowDetailsIntentionAction(
        override val annotationMessage: String,
        private val forImage: ContainerIssuesForImage,
        private val severityToShow: Severity
    ) : ShowDetailsIntentionActionBase() {

        override fun selectNodeAndDisplayDescription(toolWindowPanel: SnykToolWindowPanel) {
            toolWindowPanel.selectNodeAndDisplayDescription(forImage)
        }

        override fun getSeverity(): Severity = severityToShow
    }
}
