package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
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
import javax.swing.JPanel
import snyk.common.lsp.SnykTreeViewParams

class HtmlTreePanel(project: Project) : JPanel(), Disposable {
  private val logger = logger<HtmlTreePanel>()
  private var jbCefBrowser: com.intellij.ui.jcef.JBCefBrowser? = null

  @Volatile private var isDisposed = false
  @Volatile private var lastRawHtml: String = ""

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

    val rawHtml = HtmlTreePanel::class.java.classLoader.getResource(HTML_INIT_FILE)?.readText()
    val initHtml = getFormattedHtml(rawHtml ?: "")

    val loadHandlerGenerators = emptyList<LoadHandlerGenerator>().toMutableList()
    val treeViewBridgeHandler = TreeViewBridgeHandler(project)
    loadHandlerGenerators += { treeViewBridgeHandler.generate(it) }

    logger.debug("HtmlTreePanel: creating JCEF browser")
    jbCefBrowser = JCEFUtils.getJBCefBrowserIfSupported(initHtml, loadHandlerGenerators)
    logger.debug("HtmlTreePanel: JCEF browser created, isNull=${jbCefBrowser == null}")

    val browser = jbCefBrowser
    if (browser != null) {
      browser.component.preferredSize = preferredSize
      add(browser.component, constraints)

      project.messageBus
        .connect(this)
        .subscribe(
          SnykTreeViewListener.SNYK_TREE_VIEW_TOPIC,
          object : SnykTreeViewListener {
            override fun onTreeViewReceived(params: SnykTreeViewParams) {
              logger.debug("HtmlTreePanel: onTreeViewReceived called, isDisposed=$isDisposed")
              if (isDisposed) return
              val rawHtml = params.treeViewHtml
              if (rawHtml == lastRawHtml) {
                logger.debug("HtmlTreePanel: skipping loadHTML — content unchanged")
                return
              }
              lastRawHtml = rawHtml
              invokeLater {
                if (!isDisposed) {
                  val styledHtml = getFormattedHtml(rawHtml)
                  logger.debug("HtmlTreePanel: loading HTML into browser")
                  browser.loadHTML(styledHtml)
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

  fun reset() {
    val browser = jbCefBrowser ?: return
    if (isDisposed) return
    val rawHtml = HtmlTreePanel::class.java.classLoader.getResource(HTML_INIT_FILE)?.readText()
    val initHtml = getFormattedHtml(rawHtml ?: "")
    lastRawHtml = ""
    invokeLater {
      if (!isDisposed) {
        browser.loadHTML(initHtml)
      }
    }
  }

  fun selectNode(issueId: String) {
    val browser = jbCefBrowser ?: return
    if (isDisposed) return
    val escaped = issueId.replace("\\", "\\\\").replace("\"", "\\\"")
    invokeLater {
      if (!isDisposed) {
        browser.cefBrowser.executeJavaScript(
          "if (window.__selectTreeNode__) window.__selectTreeNode__(\"$escaped\");",
          browser.cefBrowser.url,
          0,
        )
      }
    }
  }

  override fun dispose() {
    isDisposed = true
    jbCefBrowser?.dispose()
    jbCefBrowser = null
  }

  companion object {
    const val HTML_INIT_FILE = "html/TreeViewInit.html"
  }
}
