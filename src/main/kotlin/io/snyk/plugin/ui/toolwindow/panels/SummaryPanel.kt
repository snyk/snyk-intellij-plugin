package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.util.minimumHeight
import com.intellij.ui.util.minimumWidth
import com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER
import io.snyk.plugin.events.SnykScanSummaryListenerLS
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.getStandardLayout
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.LoadHandlerGenerator
import io.snyk.plugin.ui.jcef.ThemeBasedStylingGenerator
import io.snyk.plugin.ui.jcef.ToggleDeltaHandler
import io.snyk.plugin.ui.toolwindow.panels.PanelHTMLUtils.Companion.getFormattedHtml
import snyk.common.lsp.SnykScanSummaryParams
import java.awt.BorderLayout
import javax.swing.JPanel

class SummaryPanel(project: Project) : JPanel(BorderLayout()), Disposable {
    init {
        name = "summaryPanel"
        minimumHeight = 0
        minimumWidth = 0

        // Initialise browser layout
        layout = getStandardLayout(1, 1)
        val rawHtml = SummaryPanel::class.java.classLoader.getResource(HTML_INIT_FILE)?.readText()
        val styledHtml = ThemeBasedStylingGenerator.replaceWithCustomStyles(rawHtml?: "")
        val browser = JBCefBrowser()
        browser.loadHTML(styledHtml, browser.cefBrowser.url)
        add(browser.component, baseGridConstraints(row = 0, column = 0, anchor = ANCHOR_CENTER))

        // Subscribe to scan summaries
        project.messageBus.connect(this)
            .subscribe(SnykScanSummaryListenerLS.SNYK_SCAN_SUMMARY_TOPIC, object : SnykScanSummaryListenerLS {
                override fun onSummaryReceived(summaryParams: SnykScanSummaryParams) {
                    super.onSummaryReceived(summaryParams)
                    val formattedHtml = getFormattedHtml(summaryParams.scanSummary)
                    val loadHandlerGenerators = emptyList<LoadHandlerGenerator>().toMutableList()
                    val toggleDeltaHandler = ToggleDeltaHandler()
                    loadHandlerGenerators += { toggleDeltaHandler.generate(it) }
                    val jbCefBrowserComponent =
                        JCEFUtils.getJBCefBrowserComponentIfSupported(formattedHtml, loadHandlerGenerators)
                    jbCefBrowserComponent?.let {
                        remove(0)
                        add(it, baseGridConstraints(row = 0, column = 0, anchor = ANCHOR_CENTER))
                        validate()
                    }
                }
            })
    }

    override fun dispose() {}

    companion object {
        const val HTML_INIT_FILE = "html/ScanSummaryInit.html"
    }
}
