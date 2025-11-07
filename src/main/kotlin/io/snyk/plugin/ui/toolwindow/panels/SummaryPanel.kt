package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
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
import snyk.common.lsp.SnykScanSummaryParams
import java.awt.Dimension

class SummaryPanel(project: Project) : SimpleToolWindowPanel(true, true), Disposable {
    init {
        name = "summaryPanel"
        minimumSize = Dimension(0, 0)

        // Initialise browser layout
        layout = getStandardLayout(1, 1)

        // By default, the browser component has a minimum size of 800 x 600 pixels, which prevents it from resizing
        // along with the parent panel. We override the minimum size to 1 pixel x 1 pixel (this must be non-zero), and
        // use constrains to match the parent's size.
        val preferredSize = Dimension(1, 1)
        val constraints =  baseGridConstraints(
            row = 0,
            column = 0,
            anchor = ANCHOR_NORTHWEST,
            fill = FILL_BOTH,
            indent = 0,
            useParentLayout = true
        )

        val rawHtml = SummaryPanel::class.java.classLoader.getResource(HTML_INIT_FILE)?.readText()
        var styledHtml = getFormattedHtml(rawHtml?: "")

        // Create handlers for the toggles between all issues and delta findings
        val loadHandlerGenerators = emptyList<LoadHandlerGenerator>().toMutableList()
        val toggleDeltaHandler = ToggleDeltaHandler(project)
        loadHandlerGenerators += { toggleDeltaHandler.generate(it) }

        val jbCefBrowser =
            JCEFUtils.getJBCefBrowserIfSupported(styledHtml, loadHandlerGenerators)

        if (jbCefBrowser != null) {
            jbCefBrowser.component.preferredSize = preferredSize
            jbCefBrowser.loadHTML(styledHtml, jbCefBrowser.cefBrowser.url)
            add(jbCefBrowser.component, constraints)

            // Subscribe to scan summaries
            project.messageBus.connect(this)
                .subscribe(SnykScanSummaryListener.SNYK_SCAN_SUMMARY_TOPIC, object : SnykScanSummaryListener {
                    // Replace the current HTML with the new HTML from the Language Server
                    override fun onSummaryReceived(summaryParams: SnykScanSummaryParams) {
                        styledHtml = getFormattedHtml(summaryParams.scanSummary)
                            .replace("\${ideFunc}", "window.toggleDeltaQuery(isEnabled);")
                        jbCefBrowser.loadHTML(styledHtml)
                    }
                })

        } else {
            val ideName = ApplicationNamesInfo.getInstance().fullProductName
            SnykBalloonNotificationHelper.showError(
                "Failed to show issue summary. Please make sure you are running the latest version of $ideName.",
                null
            )
        }
    }

    override fun dispose() {}

    companion object {
        const val HTML_INIT_FILE = "html/ScanSummaryInit.html"
    }
}
