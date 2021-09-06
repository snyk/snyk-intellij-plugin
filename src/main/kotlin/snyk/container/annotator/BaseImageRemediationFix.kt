package snyk.container.annotator

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.FileContentUtil
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.psi.impl.YAMLKeyValueImpl
import snyk.container.ContainerIssuesForFile

class BaseImageRemediationFix(
    private val containerIssuesForFile: ContainerIssuesForFile
) : IntentionAction {
    private val LOG = logger<BaseImageRemediationFix>()

    override fun startInWriteAction(): Boolean {
        return true
    }

    override fun getText(): String {
        return "Upgrade Docker image"
    }

    override fun getFamilyName(): String {
        return "Snyk Container"
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return containerIssuesForFile.docker.baseImageRemediation.isRemediationAvailable()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        LOG.warn("BaseImageRemediationFix invoke starting...")

        val imageRemediationInfo = containerIssuesForFile.baseImageRemediationInfo
        if (imageRemediationInfo == null) {
            LOG.warn("baseImageRemediationInfo is null, skip fixing...")
            return
        }

        var imageNameToFix = ""

        // check major updates
        if (imageRemediationInfo.majorUpgrades != null) {
            LOG.warn("Major upgrade available: ${imageRemediationInfo.majorUpgrades}")
            imageNameToFix = imageRemediationInfo.majorUpgrades.name
        } else if (imageRemediationInfo.minorUpgrades != null) {
            LOG.warn("Minor upgrade available: ${imageRemediationInfo.minorUpgrades}")
            imageNameToFix = imageRemediationInfo.minorUpgrades.name
        } else if (imageRemediationInfo.alternativeUpgrades != null) {
            LOG.warn("Alternative upgrade available: ${imageRemediationInfo.alternativeUpgrades}")
            imageNameToFix = imageRemediationInfo.alternativeUpgrades.name
        }

        val doc = editor?.document!!
        val startOffset = doc.getLineStartOffset(containerIssuesForFile.lineNumber.toInt())
        val psiElementStart = file?.findElementAt(startOffset)
        val yamlKeyValuePsiElement = psiElementStart?.nextSibling as YAMLKeyValueImpl

        WriteCommandAction.runWriteCommandAction(project) {
            val fix = YAMLElementGenerator.getInstance(project).createYamlKeyValue(
                yamlKeyValuePsiElement.keyText,
                imageNameToFix
            )
            yamlKeyValuePsiElement.replace(fix)

            FileContentUtil.reparseOpenedFiles()
            DaemonCodeAnalyzer.getInstance(project).restart(file)
        }
    }
}
