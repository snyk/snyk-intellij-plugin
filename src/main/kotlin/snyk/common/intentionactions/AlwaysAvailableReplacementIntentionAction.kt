package snyk.common.intentionactions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.DocumentUtil
import icons.SnykIcons
import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.analytics.QuickFixIsTriggered
import javax.swing.Icon

class AlwaysAvailableReplacementIntentionAction(
    val range: TextRange,
    val replacementText: String,
    val message: String = "",
    val analyticsService: SnykAnalyticsService = getSnykAnalyticsService()
) : SnykIntentionActionBase() {

    override fun getIcon(@IconFlags flags: Int): Icon = SnykIcons.CHECKMARK_GREEN

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun getText(): String = intentionTextPrefix + replacementText + intentionTextPostfix

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
        val event = QuickFixIsTriggered.builder()
            .ide(QuickFixIsTriggered.Ide.JETBRAINS)
            .quickFixType(arrayOf(AlwaysAvailableReplacementIntentionAction::class.simpleName))
            .build()
        analyticsService.logQuickFixIsTriggered(event)
    }

    companion object {
        private const val intentionTextPrefix = "Upgrade to "
        private const val intentionTextPostfix = " (Snyk)"
    }
}
