package snyk.code.annotator

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import icons.SnykIcons
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.TextEdit
import snyk.common.ProductType
import snyk.common.intentionactions.SnykIntentionActionBase
import snyk.common.lsp.DocumentChanger
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue
import java.util.concurrent.TimeUnit
import javax.swing.Icon

private const val TIMEOUT = 120L

class CodeActionIntention(
    private val issue: ScanIssue,
    private val codeAction: CodeAction,
    private val product: ProductType,
) :  SnykIntentionActionBase() {
    private var changes: Map<String, List<TextEdit>>? = null

    override fun getText(): String = codeAction.title

    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
        val task = object : Task.Backgroundable(project, this.title()) {
            override fun run(p0: ProgressIndicator) {
                val languageServer = LanguageServerWrapper.getInstance().languageServer
                var resolvedCodeAction = codeAction
                if (codeAction.command == null && codeAction.edit == null) {
                    resolvedCodeAction =
                        languageServer.textDocumentService
                            .resolveCodeAction(codeAction).get(TIMEOUT, TimeUnit.SECONDS)

                    val edit = resolvedCodeAction.edit
                    if (edit == null || edit.changes == null) return
                    changes = edit.changes
                } else {
                    val codeActionCommand = resolvedCodeAction.command
                    val executeCommandParams =
                        ExecuteCommandParams(codeActionCommand.command, codeActionCommand.arguments)
                    languageServer.workspaceService
                        .executeCommand(executeCommandParams).get(TIMEOUT, TimeUnit.SECONDS)
                }
            }

            override fun onSuccess() {
                // see https://intellij-support.jetbrains.com/hc/en-us/community/posts/360005049419-Run-WriteAction-in-Background-process-Asynchronously
                // save the write action to call it later onSuccess
                // as we are updating documents, we need a WriteCommandAction
                WriteCommandAction.runWriteCommandAction(project) {
                    if (changes == null) return@runWriteCommandAction
                    for (change in changes!!) {
                        DocumentChanger.applyChange(change)
                    }
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }


    override fun getIcon(p0: Int): Icon {
        return when (product) {
            ProductType.OSS -> {
                if (this.codeAction.title.startsWith("Upgrade to")) {
                    SnykIcons.CHECKMARK_GREEN
                } else {
                    SnykIcons.OPEN_SOURCE_SECURITY
                }
            }
            ProductType.IAC -> SnykIcons.IAC
            ProductType.CONTAINER -> SnykIcons.CONTAINER
            ProductType.CODE_SECURITY -> SnykIcons.SNYK_CODE
            ProductType.CODE_QUALITY -> SnykIcons.SNYK_CODE
            ProductType.ADVISOR -> TODO()

        }
    }

    override fun getPriority(): PriorityAction.Priority {
        return when {
            codeAction.title.contains("fix", ignoreCase = true) -> PriorityAction.Priority.TOP
            codeAction.title.contains("Upgrade to", ignoreCase = true) -> PriorityAction.Priority.TOP
            else -> issue.getSeverityAsEnum().getQuickFixPriority()
        }
    }

    fun title(): String {
        return when (product) {
            ProductType.OSS -> "Applying Snyk OpenSource Action"
            ProductType.IAC -> "Applying Snyk Infrastructure as Code Action"
            ProductType.CONTAINER -> "Applying Snyk Container Action"
            ProductType.CODE_SECURITY -> "Applying Snyk Code Action"
            ProductType.CODE_QUALITY -> "Applying Snyk Code Action"
            ProductType.ADVISOR -> TODO()
        }
    }
}
