package snyk.common.intentionactions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.FileContentUtil

class AlwaysAvailableReplacementIntentionAction(
    val range: TextRange,
    val replacementText: String,
    private val intentionText: String = intentionDefaultTextPrefix,
    private val familyName: String = intentionDefaultFamilyName
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
        val doc = editor?.document ?: return
        WriteAction.run<RuntimeException> {
            doc.replaceString(range.startOffset, range.endOffset, replacementText)
            FileContentUtil.reparseOpenedFiles()
            // save all changes on disk to update caches through SnykBulkFileListener
            FileDocumentManager.getInstance().saveDocument(doc)
            DaemonCodeAnalyzer.getInstance(project).restart(file)
        }
    }

    companion object {
        private const val intentionDefaultTextPrefix = "Change to "
        private const val intentionDefaultFamilyName = "Snyk"
    }
}


