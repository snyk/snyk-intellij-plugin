package io.snyk.plugin.ui.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.jcef.SaveConfigHandler
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.common.lsp.LanguageServerWrapper
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class HTMLSettingsPanel(
    private val project: Project
) : JPanel(BorderLayout()), Disposable {

    private val logger = Logger.getInstance(HTMLSettingsPanel::class.java)
    private var jbCefBrowser: JBCefBrowser? = null
    private var isUsingFallback = false
    @Volatile
    private var modified = false

    init {
        Disposer.register(SnykPluginDisposable.getInstance(project), this)
        initializePanel()
        subscribeToCliDownloadEvents()
    }

    private fun initializePanel() {
        if (!JBCefApp.isSupported()) {
            showJcefNotSupportedMessage()
            return
        }

        val html = getHtmlContent()
        if (html != null) {
            initializeJcefBrowser(html)
        } else {
            showErrorMessage("Failed to load settings panel")
        }
    }

    private fun getHtmlContent(): String? {
        val lsWrapper = LanguageServerWrapper.getInstance(project)

        val lsHtml = lsWrapper.getConfigHtml()
        logger.debug("getHtmlContent: lsHtml is ${if (lsHtml != null) "available (${lsHtml.length} chars)" else "null"}")
        if (lsHtml != null && lsHtml.isNotBlank()) {
            isUsingFallback = false
            return lsHtml
        }

        logger.debug("getHtmlContent: using fallback HTML")
        isUsingFallback = true
        return loadFallbackHtml()
    }

    private fun loadFallbackHtml(): String? {
        return try {
            val inputStream = javaClass.classLoader.getResourceAsStream("html/settings-fallback.html")
                ?: return null

            val template = inputStream.bufferedReader().use { it.readText() }
            val settings = pluginSettings()

            template
                .replace("{{MANAGE_BINARIES_CHECKED}}", if (settings.manageBinariesAutomatically) "checked" else "")
                .replace("{{CLI_BASE_DOWNLOAD_URL}}", settings.cliBaseDownloadURL)
                .replace("{{CLI_PATH}}", settings.cliPath)
                .replace("{{CHANNEL_STABLE_SELECTED}}", if (settings.cliReleaseChannel == "stable") "selected" else "")
                .replace("{{CHANNEL_RC_SELECTED}}", if (settings.cliReleaseChannel == "rc") "selected" else "")
                .replace("{{CHANNEL_PREVIEW_SELECTED}}", if (settings.cliReleaseChannel == "preview") "selected" else "")
        } catch (e: Exception) {
            logger.warn("Failed to load fallback HTML", e)
            null
        }
    }

    private fun initializeJcefBrowser(html: String) {
        removeAll()

        val cefClient = JBCefApp.getInstance().createClient()
        cefClient.setProperty("JS_QUERY_POOL_SIZE", 1)

        jbCefBrowser = JBCefBrowserBuilder()
            .setClient(cefClient)
            .setEnableOpenDevToolsMenuItem(true)
            .setMouseWheelEventEnable(true)
            .setUrl("about:blank")
            .build()

        jbCefBrowser?.setOpenLinksInExternalBrowser(true)

        val saveConfigHandler = SaveConfigHandler(project) { modified = true }
        val loadHandler = saveConfigHandler.generateSaveConfigHandler(jbCefBrowser!!)
        cefClient.addLoadHandler(loadHandler, jbCefBrowser!!.cefBrowser)

        jbCefBrowser?.loadHTML(html, jbCefBrowser?.cefBrowser?.url ?: "about:blank")

        add(jbCefBrowser!!.component, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun subscribeToCliDownloadEvents() {
        val messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
        messageBusConnection.subscribe(
            SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
            object : SnykCliDownloadListener {
                override fun cliDownloadStarted() {}

                override fun cliDownloadFinished(succeed: Boolean) {
                    if (succeed && isUsingFallback) {
                        ApplicationManager.getApplication().invokeLater {
                            refreshWithLsConfig()
                        }
                    }
                }
            }
        )
    }

    private fun refreshWithLsConfig() {
        val lsWrapper = LanguageServerWrapper.getInstance(project)
        val lsHtml = lsWrapper.getConfigHtml()

        if (lsHtml != null) {
            isUsingFallback = false
            initializeJcefBrowser(lsHtml)
        }
    }

    private fun showJcefNotSupportedMessage() {
        removeAll()
        val label = JLabel(
            "<html><center>JCEF is not supported on this platform.<br>" +
                "The HTML settings panel cannot be displayed.</center></html>",
            SwingConstants.CENTER
        )
        add(label, BorderLayout.CENTER)
        SnykBalloonNotificationHelper.showWarn(
            "JCEF is not supported on this platform. Using legacy settings dialog.",
            project
        )
    }

    private fun showErrorMessage(message: String) {
        removeAll()
        val label = JLabel("<html><center>$message</center></html>", SwingConstants.CENTER)
        add(label, BorderLayout.CENTER)
    }

    fun isModified(): Boolean {
        return modified
    }

    fun apply() {
        modified = false
        LanguageServerWrapper.getInstance(project).updateConfiguration(true)
    }

    override fun dispose() {
        jbCefBrowser?.dispose()
        jbCefBrowser = null
    }
}
