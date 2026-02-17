package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTHWEST
import com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH
import io.snyk.plugin.events.SnykTreeViewListener
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.getStandardLayout
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.LoadHandlerGenerator
import io.snyk.plugin.ui.jcef.TreeViewBridgeHandler
import io.snyk.plugin.ui.toolwindow.panels.PanelHTMLUtils.Companion.getFormattedHtml
import java.awt.Dimension
import snyk.common.lsp.SnykTreeViewParams

class HtmlTreePanel(project: Project) : SimpleToolWindowPanel(true, true), Disposable {
  private val logger = logger<HtmlTreePanel>()

  @Volatile private var isDisposed = false

  init {
    logger.debug("HtmlTreePanel init starting")
    name = "htmlTreePanel"
    minimumSize = Dimension(0, 0)

    layout = getStandardLayout(1, 1)

    val preferredSize = Dimension(1, 1)
    val constraints =
      baseGridConstraints(
        row = 0,
        column = 0,
        anchor = ANCHOR_NORTHWEST,
        fill = FILL_BOTH,
        indent = 0,
        useParentLayout = true,
      )

    val initHtml = getFormattedHtml(INIT_HTML)

    val loadHandlerGenerators = emptyList<LoadHandlerGenerator>().toMutableList()
    val treeViewBridgeHandler = TreeViewBridgeHandler(project)
    loadHandlerGenerators += { treeViewBridgeHandler.generate(it) }

    logger.debug("HtmlTreePanel: creating JCEF browser")
    val jbCefBrowser = JCEFUtils.getJBCefBrowserIfSupported(initHtml, loadHandlerGenerators)
    logger.debug("HtmlTreePanel: JCEF browser created, isNull=${jbCefBrowser == null}")

    if (jbCefBrowser != null) {
      jbCefBrowser.component.preferredSize = preferredSize
      add(jbCefBrowser.component, constraints)

      project.messageBus
        .connect(this)
        .subscribe(
          SnykTreeViewListener.SNYK_TREE_VIEW_TOPIC,
          object : SnykTreeViewListener {
            override fun onTreeViewReceived(params: SnykTreeViewParams) {
              logger.debug("HtmlTreePanel: onTreeViewReceived called, isDisposed=$isDisposed")
              if (isDisposed) return
              val rawHtml = params.treeViewHtml
              invokeLater {
                if (!isDisposed) {
                  val styledHtml = getFormattedHtml(rawHtml)
                  logger.debug("HtmlTreePanel: loading HTML into browser")
                  jbCefBrowser.loadHTML(styledHtml)
                }
              }
            }
          },
        )
    } else {
      val ideName = ApplicationNamesInfo.getInstance().fullProductName
      SnykBalloonNotificationHelper.showError(
        "Failed to show HTML tree view. Please make sure you are running the latest version of $ideName.",
        null,
      )
    }
    logger.debug("HtmlTreePanel init completed")
  }

  override fun dispose() {
    isDisposed = true
  }

  companion object {
    private const val INIT_HTML = "<html><body><p>Waiting for scan results…</p></body></html>"
  }
}
