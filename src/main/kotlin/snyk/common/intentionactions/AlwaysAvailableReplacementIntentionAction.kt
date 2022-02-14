package snyk.common.intentionactions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.DocumentUtil
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.ui.SnykBalloonNotificationHelper

class AlwaysAvailableReplacementIntentionAction(
    val range: TextRange,
    val replacementText: String,
    private val intentionText: String = intentionDefaultTextPrefix,
    private val familyName: String = intentionDefaultFamilyName,
    val message: String = ""
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
        DocumentUtil.writeInRunUndoTransparentAction {
            doc.replaceString(range.startOffset, range.endOffset, replacementText)
            // save all changes on disk to update caches through SnykBulkFileListener
            FileDocumentManager.getInstance().saveDocument(doc)
            refreshAnnotationsForOpenFiles(project)
            if (message.isNotBlank()) {
                SnykBalloonNotificationHelper.showWarn(message, project)
            }
        }
    }

    companion object {
        private const val intentionDefaultTextPrefix = "Change to "
        private const val intentionDefaultFamilyName = "Snyk"
    }
}


