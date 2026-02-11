package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_NORTHWEST
import com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH
import io.snyk.plugin.events.SnykScanSummaryListener
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.getStandardLayout
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.LoadHandlerGenerator
import io.snyk.plugin.ui.jcef.ToggleDeltaHandler
import io.snyk.plugin.ui.toolwindow.panels.PanelHTMLUtils.Companion.getFormattedHtml
import java.awt.Dimension
import snyk.common.lsp.SnykScanSummaryParams

class SummaryPanel(project: Project) : SimpleToolWindowPanel(true, true), Disposable {
  private val logger = logger<SummaryPanel>()

  @Volatile private var isDisposed = false

  init {
    logger.debug("SummaryPanel init starting")
    name = "summaryPanel"
    minimumSize = Dimension(0, 0)

    // Initialise browser layout
    layout = getStandardLayout(1, 1)

    // By default, the browser component has a minimum size of 800 x 600 pixels, which prevents it
    // from resizing
    // along with the parent panel. We override the minimum size to 1 pixel x 1 pixel (this must be
    // non-zero), and
    // use constrains to match the parent's size.
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

    val rawHtml = SummaryPanel::class.java.classLoader.getResource(HTML_INIT_FILE)?.readText()
    logger.debug("SummaryPanel: getting formatted HTML")
    var styledHtml = getFormattedHtml(rawHtml ?: "")
    logger.debug("SummaryPanel: formatted HTML obtained")

    // Create handlers for the toggles between all issues and delta findings
    val loadHandlerGenerators = emptyList<LoadHandlerGenerator>().toMutableList()
    val toggleDeltaHandler = ToggleDeltaHandler(project)
    loadHandlerGenerators += { toggleDeltaHandler.generate(it) }

    // Create browser without loading HTML - HTML will be loaded via invokeLater by JCEFUtils
    logger.debug("SummaryPanel: creating JCEF browser")
    val jbCefBrowser = JCEFUtils.getJBCefBrowserIfSupported(styledHtml, loadHandlerGenerators)
    logger.debug("SummaryPanel: JCEF browser created, isNull=${jbCefBrowser == null}")

    if (jbCefBrowser != null) {
      jbCefBrowser.component.preferredSize = preferredSize
      logger.debug("SummaryPanel: adding browser component")
      add(jbCefBrowser.component, constraints)
      logger.debug("SummaryPanel: browser component added")

      // Subscribe to scan summaries
      project.messageBus
        .connect(this)
        .subscribe(
          SnykScanSummaryListener.SNYK_SCAN_SUMMARY_TOPIC,
          object : SnykScanSummaryListener {
            // Replace the current HTML with the new HTML from the Language Server
            override fun onSummaryReceived(summaryParams: SnykScanSummaryParams) {
              logger.debug("SummaryPanel: onSummaryReceived called, isDisposed=$isDisposed")
              if (isDisposed) return
              // Capture the raw summary - defer all processing to EDT
              // getFormattedHtml accesses Swing components and must run on EDT
              val rawSummary = summaryParams.scanSummary
              logger.debug("SummaryPanel: scheduling invokeLater for HTML update")
              invokeLater {
                logger.debug("SummaryPanel: invokeLater executing, isDisposed=$isDisposed")
                if (!isDisposed) {
                  logger.debug("SummaryPanel: getting formatted HTML in invokeLater")
                  styledHtml =
                    getFormattedHtml(rawSummary)
                      .replace("\${ideFunc}", "window.toggleDeltaQuery(isEnabled);")
                  logger.debug("SummaryPanel: loading HTML into browser")
                  jbCefBrowser.loadHTML(styledHtml)
                  logger.debug("SummaryPanel: HTML loaded")
                }
              }
            }
          },
        )
    } else {
      val ideName = ApplicationNamesInfo.getInstance().fullProductName
      SnykBalloonNotificationHelper.showError(
        "Failed to show issue summary. Please make sure you are running the latest version of $ideName.",
        null,
      )
    }
    logger.debug("SummaryPanel init completed")
  }

  override fun dispose() {
    isDisposed = true
  }

  companion object {
    const val HTML_INIT_FILE = "html/ScanSummaryInit.html"
  }
}
