package io.snyk.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.PopupHandler
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.isHtmlTreeViewEnabled
import io.snyk.plugin.ui.expandTreeNodeRecursively
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import snyk.common.lsp.LsProduct
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams

/** IntelliJ ToolWindow for Snyk plugin. */
class SnykToolWindow(private val project: Project) :
  SimpleToolWindowPanel(false, true), Disposable {

  private val actionToolbar: ActionToolbar

  init {
    val snykToolWindowPanel = getSnykToolWindowPanel(project)!!
    val tree = snykToolWindowPanel.getTree()
    val actionManager = ActionManager.getInstance()
    val actionGroup = DefaultActionGroup()

    actionGroup.addAll(actionManager.getAction("io.snyk.plugin.ScanActions") as DefaultActionGroup)

    if (!isHtmlTreeViewEnabled() || !com.intellij.ui.jcef.JBCefApp.isSupported()) {
      actionGroup.addSeparator()
      actionGroup.addAll(
        actionManager.getAction("io.snyk.plugin.ViewActions") as DefaultActionGroup
      )

      val expandTreeActionsGroup = DefaultActionGroup()
      val myTreeExpander = DefaultTreeExpander(tree)
      val commonActionsManager = CommonActionsManager.getInstance()
      expandTreeActionsGroup.add(commonActionsManager.createExpandAllAction(myTreeExpander, this))
      expandTreeActionsGroup.add(commonActionsManager.createCollapseAllAction(myTreeExpander, this))

      val expandNodeChildActionsGroup = DefaultActionGroup()
      expandNodeChildActionsGroup.add(ExpandNodeChildAction(tree))
      PopupHandler.installPopupMenu(tree, expandNodeChildActionsGroup, "SnykTree")

      actionGroup.addAll(expandTreeActionsGroup)
    }

    actionGroup.addSeparator()
    actionGroup.addAll(actionManager.getAction("io.snyk.plugin.MiscActions") as DefaultActionGroup)

    actionToolbar = actionManager.createActionToolbar("Snyk Toolbar", actionGroup, false)
    actionToolbar.targetComponent = this
    initialiseToolbarUpdater()
    toolbar = actionToolbar.component

    setContent(snykToolWindowPanel)
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

  class ExpandNodeChildAction(val tree: JTree) :
    DumbAwareAction("Expand All Children", "Expand All Children", AllIcons.Actions.Expandall) {
    override fun actionPerformed(e: AnActionEvent) {
      val selectedNode = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return
      expandTreeNodeRecursively(tree, selectedNode)
    }
  }
}
