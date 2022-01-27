package snyk.common.intentionactions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.FileContentUtil

class AlwaysAvailableReplacementIntentionAction(
    val range: TextRange,
    val replacementText: String,
    private val intentionText: String = "Change to ",
    private val familyName: String = "Snyk"
) : IntentionAction {

    override fun startInWriteAction(): Boolean {
        return true
    }

    override fun getText(): String {
        return intentionText + replacementText
    }

    override fun getFamilyName(): String {
        return familyName
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val doc = editor!!.document
        WriteAction.run<RuntimeException> {
            doc.replaceString(range.startOffset, range.endOffset, replacementText)
            FileContentUtil.reparseOpenedFiles()
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
}


