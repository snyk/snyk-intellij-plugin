package snyk.container.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.services.SnykAnalyticsService
import snyk.analytics.QuickFixIsDisplayed
import snyk.analytics.QuickFixIsTriggered
import snyk.container.BaseImageRemediationInfo
import snyk.container.ContainerIssuesForImage

class BaseImageRemediationFix(
    private val containerIssuesForImage: ContainerIssuesForImage,
    private val range: TextRange,
    private val analyticsService: SnykAnalyticsService = service()
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
        val event = QuickFixIsDisplayed.builder()
            .ide(QuickFixIsDisplayed.Ide.JETBRAINS)
            .quickFixType(arrayOf(BaseImageRemediationFix::class.simpleName))
            .build()
        analyticsService?.logQuickFixIsDisplayed(event)
        return "Upgrade Image to $imageNameToFix"
    }

    override fun getFamilyName(): String {
        return "Snyk Container"
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
        val event = QuickFixIsTriggered.builder()
            .ide(QuickFixIsTriggered.Ide.JETBRAINS)
            .quickFixType(arrayOf(BaseImageRemediationFix::class.simpleName))
            .build()
        analyticsService.logQuickFixIsTriggered(event)
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
