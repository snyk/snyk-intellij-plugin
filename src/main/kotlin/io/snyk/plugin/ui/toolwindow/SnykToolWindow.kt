package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getSnykToolWindowPanel
import snyk.common.lsp.LsProduct
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams

/** IntelliJ ToolWindow for Snyk plugin. */
class SnykToolWindow(private val project: Project) :
  SimpleToolWindowPanel(false, true), Disposable {

  private val actionToolbar: ActionToolbar

  init {
    val actionManager = ActionManager.getInstance()
    val actionGroup = DefaultActionGroup()

    actionGroup.addAll(actionManager.getAction("io.snyk.plugin.ScanActions") as DefaultActionGroup)
    actionGroup.addSeparator()
    actionGroup.addAll(actionManager.getAction("io.snyk.plugin.MiscActions") as DefaultActionGroup)

    actionToolbar = actionManager.createActionToolbar("Snyk Toolbar", actionGroup, false)
    actionToolbar.targetComponent = this
    initialiseToolbarUpdater()
    toolbar = actionToolbar.component

    setContent(getSnykToolWindowPanel(project)!!)
  }

  private fun initialiseToolbarUpdater() {
    // update actions presentation immediately after running state changes (avoid default 500 ms
    // delay)
    project.messageBus
      .connect(this)
      .subscribe(
        SnykScanListener.SNYK_SCAN_TOPIC,
        object : SnykScanListener {
          override fun scanningSnykCodeFinished() {
            updateActionsPresentation()
          }

          override fun scanningOssFinished() {
            updateActionsPresentation()
          }

          override fun scanningIacFinished() {
            updateActionsPresentation()
          }

          override fun scanningError(snykScan: SnykScanParams) {
            updateActionsPresentation()
          }

          override fun onPublishDiagnostics(
            product: LsProduct,
            snykFile: SnykFile,
            issues: Set<ScanIssue>,
          ) = Unit
        },
      )

    project.messageBus
      .connect(this)
      .subscribe(
        SnykTaskQueueListener.TASK_QUEUE_TOPIC,
        object : SnykTaskQueueListener {
          override fun stopped() = updateActionsPresentation()
        },
      )
  }

  private fun updateActionsPresentation() =
    ApplicationManager.getApplication().invokeLater { actionToolbar.updateActionsAsync() }

  var isDisposed = false

  override fun dispose() {
    isDisposed = true
  }
}
