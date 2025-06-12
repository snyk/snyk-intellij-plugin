package snyk.common.intentionactions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile

abstract class SnykIntentionActionBase : IntentionAction, Iconable, PriorityAction {

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = DEFAULT_FAMILY_NAME

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    companion object {
        private const val DEFAULT_FAMILY_NAME = "Snyk"
    }
}
