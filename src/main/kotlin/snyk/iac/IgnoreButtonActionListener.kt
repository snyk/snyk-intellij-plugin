package snyk.iac

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import snyk.common.IgnoreService
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton

class IgnoreButtonActionListener(
    private val ignoreService: IgnoreService,
    val issueId: String,
    private val project: Project
) :
    ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
        ProgressManager.getInstance().run(IgnoreTask(project, ignoreService, issueId, e))
    }

    class IgnoreTask(project: Project, val ignoreService: IgnoreService, val issueId: String, val e: ActionEvent?) :
        Task.Backgroundable(project, "Ignoring issue...") {
        override fun run(@NotNull progressIndicator: ProgressIndicator) {
            ignoreService.ignore(issueId)
            (e?.source as? JButton)?.apply {
                isEnabled = false
                text = "Issue now ignored"
            }
        }
    }
}
