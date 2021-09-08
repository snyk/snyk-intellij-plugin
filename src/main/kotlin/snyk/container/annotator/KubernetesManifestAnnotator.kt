package snyk.container.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import snyk.container.ContainerIssuesForFile
import snyk.container.ContainerResult

class KubernetesManifestAnnotator : ExternalAnnotator<PsiFile, List<ContainerIssuesForFile>>() {
    private val LOG = logger<KubernetesManifestAnnotator>()

    override fun collectInformation(file: PsiFile): PsiFile {
        LOG.info("calling collectInformation on file: $file")
        return file
    }

    override fun doAnnotate(psiFile: PsiFile?): List<ContainerIssuesForFile> {
        LOG.info("calling doAnnotation on file: $psiFile")

        val results = psiFile?.project?.service<SnykToolWindowPanel>()?.currentContainerResult ?: return emptyList()
        ProgressManager.checkCanceled()

        return getAnnotationForFile(psiFile, results)
    }

    private fun getAnnotationForFile(psiFile: PsiFile, containerResult: ContainerResult): List<ContainerIssuesForFile> {
        val filename = psiFile.name
        LOG.info(">>> file name: $filename")

        return containerResult.allCliIssues?.filter {
            it.targetFile == filename
        } ?: emptyList()
    }

    override fun apply(file: PsiFile, annotations: List<ContainerIssuesForFile>?, holder: AnnotationHolder) {
        if (annotations == null || annotations.isEmpty()) {
            return
        }

        LOG.info("Received ${annotations.size} annotations")
        for (annotation in annotations) {
            if (annotation.docker.baseImageRemediation?.isRemediationAvailable() == true) {
                generateAnnotationForBaseImageUpgrade(file, annotation, holder)
            }
        }
    }

    private fun generateAnnotationForBaseImageUpgrade(
        psiFile: PsiFile,
        containerIssuesForFile: ContainerIssuesForFile,
        holder: AnnotationHolder
    ) {
        val message = "Recommendations for upgrading the base image"

        val document = psiFile.viewProvider.document!!
        val startOffset = document.getLineStartOffset(containerIssuesForFile.lineNumber.toInt())
        val psiElementStart = psiFile.findElementAt(startOffset)
        val yamlKeyValuePsiElement = psiElementStart?.nextSibling as YAMLKeyValueImpl

        LOG.warn(">>> ${yamlKeyValuePsiElement.keyText} - ${yamlKeyValuePsiElement.valueText}")
        if (yamlKeyValuePsiElement.keyText == "image" &&
            yamlKeyValuePsiElement.valueText == containerIssuesForFile.imageName
        ) {
            val annotationBuilder = holder.newAnnotation(HighlightSeverity.ERROR, message)
                .highlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                .range(errorTextRange(psiFile, containerIssuesForFile))

            annotationBuilder.withFix(BaseImageRemediationFix(containerIssuesForFile))

            annotationBuilder.create()
        }
    }

    private fun errorTextRange(file: PsiFile, containerIssuesForFile: ContainerIssuesForFile): TextRange {
        val doc = file.viewProvider.document!!
        val startOffset = doc.getLineStartOffset(containerIssuesForFile.lineNumber.toInt())
        val psiElementStart = file.findElementAt(startOffset)
        val yamlKeyValuePsiElement = psiElementStart?.nextSibling as YAMLKeyValueImpl

        return yamlKeyValuePsiElement.value?.textRange!!
    }
}
