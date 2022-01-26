package snyk.iac

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.calcRelativeToProjectPath
import com.intellij.psi.PsiFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.jetbrains.annotations.NotNull
import snyk.common.IgnoreException
import snyk.common.IgnoreService
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton

class IgnoreButtonActionListener(
    private val ignoreService: IgnoreService,
    val issue: IacIssue,
    private val psiFile: PsiFile?,
    private val project: Project
) : ActionListener {

    override fun actionPerformed(e: ActionEvent?) {
        ProgressManager.getInstance().run(IgnoreTask(project, ignoreService, issue, psiFile, e))
    }

    class IgnoreTask(
        project: Project,
        val ignoreService: IgnoreService,
        val issue: IacIssue,
        private val psiFile: PsiFile?,
        val e: ActionEvent?
    ) : Task.Backgroundable(project, "Ignoring issue...") {
        override fun run(@NotNull progressIndicator: ProgressIndicator) {
            try {
                if (psiFile != null) {
                    val path = ignoreService.buildPath(
                        issue,
                        calcRelativeToProjectPath(psiFile.virtualFile, project).replace(".../", "")
                    )
                    ignoreService.ignoreInstance(issue.id, path)
                } else {
                    ignoreService.ignore(issue.id)
                }

                issue.ignored = true
                (e?.source as? JButton)?.apply {
                    isEnabled = false
                    text = IGNORED_ISSUE_BUTTON_TEXT
                }
                ApplicationManager.getApplication().invokeLater {
                    if (psiFile != null) {
                        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                    } else {
                        DaemonCodeAnalyzer.getInstance(project).restart()
                    }
                }
            } catch (e: IgnoreException) {
                SnykBalloonNotificationHelper.showError(
                    "Ignoring did not succeed. Error message: ${e.message})",
                    project
                )
            }
        }
    }

    companion object {
        const val IGNORED_ISSUE_BUTTON_TEXT = "Issue Is Ignored"
    }
}
