package snyk.container.annotator

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import icons.SnykIcons
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import snyk.common.intentionactions.SnykIntentionActionBase
import snyk.container.BaseImageRemediationInfo
import snyk.container.ContainerIssuesForImage
import javax.swing.Icon

class BaseImageRemediationFix(
    private val containerIssuesForImage: ContainerIssuesForImage,
    private val range: TextRange
) : SnykIntentionActionBase() {
    private val imageNameToFix: CharSequence
    private val logger = logger<BaseImageRemediationFix>()

    init {
        val imageRemediationInfo = containerIssuesForImage.baseImageRemediationInfo
        if (imageRemediationInfo?.isRemediationAvailable() != true) {
            throw IllegalArgumentException("Trying to create quick fix without remediation")
        }
        imageNameToFix = determineTargetImage(imageRemediationInfo)
    }

    override fun getIcon(@Iconable.IconFlags flags: Int): Icon = SnykIcons.CHECKMARK_GREEN

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun getText(): String {
        return "Upgrade Image to $imageNameToFix"
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return containerIssuesForImage.docker.baseImageRemediation?.isRemediationAvailable() ?: false
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        logger.debug("BaseImageRemediationFix invoke starting...")

        val doc = editor!!.document
        WriteAction.run<RuntimeException> {
            doc.replaceString(range.startOffset, range.endOffset, imageNameToFix)
            refreshAnnotationsForOpenFiles(project)
        }
    }

    companion object {
        fun determineTargetImage(imageRemediationInfo: BaseImageRemediationInfo): String {
            return when {
                imageRemediationInfo.majorUpgrades != null -> imageRemediationInfo.majorUpgrades.name
                imageRemediationInfo.minorUpgrades != null -> imageRemediationInfo.minorUpgrades.name
                imageRemediationInfo.alternativeUpgrades != null -> imageRemediationInfo.alternativeUpgrades.name
                else -> throw IllegalStateException("At this point of time we should always have remediation info.")
            }
        }
    }
}
