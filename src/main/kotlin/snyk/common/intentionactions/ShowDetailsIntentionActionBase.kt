package snyk.common.intentionactions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.snykToolWindow
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import javax.swing.Icon

abstract class ShowDetailsIntentionActionBase : SnykIntentionActionBase() {

  protected abstract val annotationMessage: String

  override fun getText(): String = annotationMessage

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    snykToolWindow(project)?.show()
    getSnykToolWindowPanel(project)?.let { selectNodeAndDisplayDescription(it) }
  }

  override fun getIcon(flags: Int): Icon = getSeverity().getIcon()

  override fun getPriority(): PriorityAction.Priority = getSeverity().getQuickFixPriority()

  abstract fun selectNodeAndDisplayDescription(toolWindowPanel: SnykToolWindowPanel)

  abstract fun getSeverity(): Severity
}
