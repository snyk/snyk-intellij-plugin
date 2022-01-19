package snyk.container.annotator

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.FileContentUtil
import snyk.container.BaseImageRemediationInfo
import snyk.container.ContainerIssuesForImage

class BaseImageRemediationFix(
    private val containerIssuesForImage: ContainerIssuesForImage,
    private val range: TextRange
) : IntentionAction {
    private var imageNameToFix: CharSequence
    private val logger = logger<BaseImageRemediationFix>()

    init {
        if (containerIssuesForImage.baseImageRemediationInfo == null)
            throw IllegalArgumentException("Trying to create quick fix without remediation")

        val imageRemediationInfo = containerIssuesForImage.baseImageRemediationInfo
        imageNameToFix = determineTargetImage(imageRemediationInfo)
    }

    override fun startInWriteAction(): Boolean {
        return true
    }

    override fun getText(): String {
        return "Upgrade Image to $imageNameToFix"
    }

    override fun getFamilyName(): String {
        return "Snyk Container"
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return containerIssuesForImage.docker.baseImageRemediation?.isRemediationAvailable() ?: false
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        logger.warn("BaseImageRemediationFix invoke starting...")

        val doc = editor?.document!!
        WriteCommandAction.runWriteCommandAction(project) {
            doc.replaceString(range.startOffset, range.endOffset, imageNameToFix)
            FileContentUtil.reparseOpenedFiles()
            DaemonCodeAnalyzer.getInstance(project).restart(file)
        }
    }

    companion object {
        fun determineTargetImage(imageRemediationInfo: BaseImageRemediationInfo): String {
            return if (imageRemediationInfo.majorUpgrades != null) {
                imageRemediationInfo.majorUpgrades.name
            } else if (imageRemediationInfo.minorUpgrades != null) {
                imageRemediationInfo.minorUpgrades.name
            } else if (imageRemediationInfo.alternativeUpgrades != null) {
                imageRemediationInfo.alternativeUpgrades.name
            } else
                throw IllegalStateException("At this point of time we should always have remediation info.")
        }
    }
}
