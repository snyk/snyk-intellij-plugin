package io.snyk.plugin.ui.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.runInBackground
import io.snyk.plugin.settings.executePostApplySettings
import io.snyk.plugin.settings.handleDeltaFindingsChange
import io.snyk.plugin.settings.handleReleaseChannelChange
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.SaveConfigHandler
import io.snyk.plugin.ui.jcef.ThemeBasedStylingGenerator
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.common.lsp.LanguageServerWrapper
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class HTMLSettingsPanel(
    private val project: Project
) : JPanel(BorderLayout()), Disposable {

    private val logger = Logger.getInstance(HTMLSettingsPanel::class.java)
    private var jbCefBrowser: JBCefBrowser? = null
    private var isUsingFallback = false
    private val modified = AtomicBoolean(false)
    private var currentNonce: String = JCEFUtils.generateNonce()

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

        // Show loading state immediately to avoid blocking
        showLoadingMessage()

        // Load HTML asynchronously to avoid blocking the settings dialog
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = getHtmlContent()
            val lsError = lastLsError
            // Use ModalityState.any() to ensure callback runs even in modal dialogs
            ApplicationManager.getApplication().invokeLater({
                if (html != null) {
                    initializeJcefBrowser(html)
                    // Show LS error if we're using fallback due to an error
                    if (isUsingFallback && lsError != null) {
                        showLsErrorInBrowser(lsError)
                    }
                } else {
                    showErrorMessage("Failed to load settings panel")
                }
            }, ModalityState.any())
        }
    }

    private fun showLoadingMessage() {
        removeAll()
        val loadingLabel = JLabel("Loading Snyk settings...", SwingConstants.CENTER)
        add(loadingLabel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun showLsErrorInBrowser(error: String) {
        val safeError = error.replace("'", "\\'").replace("\n", " ")
        jbCefBrowser?.cefBrowser?.executeJavaScript(
            "if (typeof window.showError === 'function') { window.showError('Language Server error: $safeError'); }",
            jbCefBrowser?.cefBrowser?.url ?: "",
            0
        )
    }

    private var lastLsError: String? = null

    private fun getHtmlContent(): String? {
        val lsWrapper = LanguageServerWrapper.getInstance(project)
        lastLsError = null

        // Try to get HTML from LS, with retries if LS is still initializing
        repeat(3) { attempt ->
            try {
                val lsHtml = lsWrapper.getConfigHtml()
                logger.debug("getHtmlContent attempt ${attempt + 1}: lsHtml is ${if (lsHtml != null) "available (${lsHtml.length} chars)" else "null"}")
                if (lsHtml != null && lsHtml.isNotBlank()) {
                    isUsingFallback = false
                    return lsHtml
                }
            } catch (e: Exception) {
                logger.warn("getHtmlContent attempt ${attempt + 1} failed", e)
                lastLsError = e.message
            }
            // Wait a bit before retry if LS might still be initializing
            if (attempt < 2) {
                Thread.sleep(1000)
            }
        }

        logger.debug("getHtmlContent: using fallback HTML after retries")
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

        // Generate new nonce and replace placeholder in HTML
        currentNonce = JCEFUtils.generateNonce()
        var processedHtml = html.replace("ideNonce", currentNonce)
        
        // Apply theme styling via string replacement
        processedHtml = ThemeBasedStylingGenerator.replaceWithCustomStyles(processedHtml)

        val (cefClient, browser) = JCEFUtils.createBrowser(enableDevTools = false)
        jbCefBrowser = browser

        val saveConfigHandler = SaveConfigHandler(project) { modified.set(true) }
        val loadHandler = saveConfigHandler.generateSaveConfigHandler(jbCefBrowser!!, null, currentNonce)
        cefClient.addLoadHandler(loadHandler, jbCefBrowser!!.cefBrowser)

        jbCefBrowser?.loadHTML(processedHtml, jbCefBrowser?.cefBrowser?.url ?: "about:blank")

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
        return modified.get()
    }

    fun apply() {
        val settings = pluginSettings()

        // Capture previous values before save
        val previousReleaseChannel = settings.cliReleaseChannel
        val previousDeltaEnabled = settings.isDeltaFindingsEnabled()

        // Call getAndSaveIdeConfig() to collect form data and save (same function in LS and fallback HTML)
        jbCefBrowser?.cefBrowser?.executeJavaScript(
            "if (typeof window.getAndSaveIdeConfig === 'function') { window.getAndSaveIdeConfig(); }",
            jbCefBrowser?.cefBrowser?.url ?: "",
            0
        )
        modified.set(false)

        runInBackground("Snyk: applying settings") {
            // Handle release channel change - prompt to download new CLI
            if (settings.cliReleaseChannel != previousReleaseChannel) {
                handleReleaseChannelChange(project)
            }

            // Handle delta findings change - clear caches
            if (settings.isDeltaFindingsEnabled() != previousDeltaEnabled) {
                handleDeltaFindingsChange(project)
            }

            executePostApplySettings(project)
        }
    }

    override fun dispose() {
        jbCefBrowser?.dispose()
        jbCefBrowser = null
    }
}
