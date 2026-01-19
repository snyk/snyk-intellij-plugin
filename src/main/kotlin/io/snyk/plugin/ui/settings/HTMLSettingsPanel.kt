package io.snyk.plugin.ui.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.getSnykTaskQueueService
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
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class HTMLSettingsPanel(
    private val project: Project
) : JPanel(BorderLayout()), Disposable {

    private val logger = Logger.getInstance(HTMLSettingsPanel::class.java)
    private var jbCefClient: JBCefClient? = null
    private var jbCefBrowser: JBCefBrowser? = null
    @Volatile
    private var isUsingFallback = false
    @Volatile
    private var isDisposed = false
    private val modified = AtomicBoolean(false)
    private var currentNonce: String = JCEFUtils.generateNonce()
    @Volatile
    private var lastLsError: String? = null
    private var saveConfigHandler: SaveConfigHandler? = null
    private val saveSemaphore = Semaphore(0)

    init {
        // Set panel background to match IDE theme (prevents white flash)
        background = UIUtil.getPanelBackground()

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
            if (isDisposed) return@executeOnPooledThread
            val html = getHtmlContent()
            val lsError = lastLsError
            // Use ModalityState.any() to ensure callback runs even in modal dialogs
            ApplicationManager.getApplication().invokeLater({
                if (isDisposed) return@invokeLater
                if (html != null) {
                    initializeJcefBrowser(html)
                    // Show LS error if we're using fallback due to an error
                    if (isUsingFallback && lsError != null) {
                        showLsErrorInBrowser(lsError)
                    }
                } else {
                    showSettingsLoadError()
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
        // Defer JavaScript execution to avoid EDT blocking
        ApplicationManager.getApplication().invokeLater {
            jbCefBrowser?.cefBrowser?.executeJavaScript(
                "if (typeof window.showError === 'function') { window.showError('Language Server error: $safeError'); }",
                jbCefBrowser?.cefBrowser?.url ?: "",
                0
            )
        }
    }

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
                .replace(
                    "{{CHANNEL_PREVIEW_SELECTED}}",
                    if (settings.cliReleaseChannel == "preview") "selected" else ""
                )
        } catch (e: Exception) {
            logger.warn("Failed to load fallback HTML", e)
            null
        }
    }

    private fun initializeJcefBrowser(html: String) {
        removeAll()

        // Dispose existing browser before creating new one
        disposeCurrentBrowser()

        // Generate new nonce and replace placeholder in HTML
        currentNonce = JCEFUtils.generateNonce()
        var processedHtml = html.replace("ideNonce", currentNonce)

        // Apply theme styling via string replacement
        processedHtml = ThemeBasedStylingGenerator.replaceWithCustomStyles(processedHtml)

        val (cefClient, browser) = JCEFUtils.createBrowser()
        jbCefClient = cefClient
        jbCefBrowser = browser

        saveConfigHandler = SaveConfigHandler(
            project = project,
            onModified = { modified.set(true) },
            onReset = { modified.set(false) },
            onSaveComplete = {
                saveSemaphore.release()
            }
        )

        val handler = saveConfigHandler ?: run {
            logger.warn("saveConfigHandler is null, cannot initialize browser handlers")
            return
        }
        val jcefBrowser = jbCefBrowser ?: run {
            logger.warn("jbCefBrowser is null, cannot initialize browser handlers")
            return
        }

        val loadHandler = handler.generateSaveConfigHandler(jcefBrowser, currentNonce)
        cefClient.addLoadHandler(loadHandler, jcefBrowser.cefBrowser)

        // Add browser to panel first (it already has IDE background from createBrowser)
        add(jcefBrowser.component, BorderLayout.CENTER)
        revalidate()
        repaint()

        // Then load the actual content
        jcefBrowser.loadHTML(processedHtml, jcefBrowser.cefBrowser.url ?: "about:blank")
    }

    private fun disposeCurrentBrowser() {
        jbCefBrowser?.dispose()
        jbCefBrowser = null
        jbCefClient?.dispose()
        jbCefClient = null
    }

    private fun subscribeToCliDownloadEvents() {
        val messageBusConnection = ApplicationManager.getApplication().messageBus.connect(this)
        messageBusConnection.subscribe(
            SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
            object : SnykCliDownloadListener {
                override fun cliDownloadStarted() {}

                override fun cliDownloadFinished(succeed: Boolean) {
                    if (succeed && isUsingFallback) {
                        refreshWithLsConfig()
                    }
                }
            }
        )
    }

    private fun refreshWithLsConfig() {
        // Fetch LS HTML in background to avoid blocking EDT
        ApplicationManager.getApplication().executeOnPooledThread {
            if (isDisposed) return@executeOnPooledThread
            val lsWrapper = LanguageServerWrapper.getInstance(project)
            val lsHtml = lsWrapper.getConfigHtml()

            if (lsHtml != null) {
                ApplicationManager.getApplication().invokeLater({
                    if (isDisposed) return@invokeLater
                    isUsingFallback = false
                    initializeJcefBrowser(lsHtml)
                }, ModalityState.any())
            }
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

    private fun showSettingsLoadError() {
        removeAll()
        val label = JLabel("<html><center>Failed to load settings panel</center></html>", SwingConstants.CENTER)
        add(label, BorderLayout.CENTER)
    }

    fun isModified(): Boolean {
        return modified.get()
    }

    fun reset() {
        // Reload HTML from language server to restore form to saved state
        ApplicationManager.getApplication().executeOnPooledThread {
            if (isDisposed) return@executeOnPooledThread
            val html = getHtmlContent()
            ApplicationManager.getApplication().invokeLater({
                if (isDisposed) return@invokeLater
                if (html != null) {
                    initializeJcefBrowser(html)
                    modified.set(false)
                }
            }, ModalityState.any())
        }
    }

    fun apply() {
        // Capture previous values before save for change detection
        previousReleaseChannel = pluginSettings().cliReleaseChannel
        previousDeltaEnabled = pluginSettings().isDeltaFindingsEnabled()
        previousManageBinariesAutomatically = pluginSettings().manageBinariesAutomatically
        previousCliPath = pluginSettings().cliPath

        val browser = jbCefBrowser?.cefBrowser
        if (browser == null) {
            logger.warn("Browser not available for apply(), running post-apply directly")
            runPostApplySettings()
            modified.set(false)
            return
        }

        // Drain any previous permits
        saveSemaphore.drainPermits()

        // Use the existing getAndSaveIdeConfig() which already works with Apply button
        // The onSaveComplete callback will release the semaphore when save completes
        // Note: executeJavaScript is non-blocking (schedules JS in JCEF), so no invokeLater needed
        browser.executeJavaScript(
            "if (typeof window.getAndSaveIdeConfig === 'function') { window.getAndSaveIdeConfig(); }",
            browser.url ?: "",
            0
        )

        // Wait for save completion using a modal progress dialog
        // This allows the EDT to process events (including JCEF callbacks) while showing progress
        var saveCompleted = false
        com.intellij.openapi.progress.ProgressManager.getInstance().runProcessWithProgressSynchronously(
            {
                saveCompleted = saveSemaphore.tryAcquire(3, TimeUnit.SECONDS)
            },
            "Saving Snyk Settings",
            false,
            project
        )

        if (saveCompleted) {
            runPostApplySettings()
        }
        modified.set(!saveCompleted)
    }

    // Stored for change detection across async save - initialized with current values
    @Volatile
    private var previousReleaseChannel: String = pluginSettings().cliReleaseChannel
    @Volatile
    private var previousDeltaEnabled: Boolean = pluginSettings().isDeltaFindingsEnabled()
    @Volatile
    private var previousManageBinariesAutomatically: Boolean = pluginSettings().manageBinariesAutomatically
    @Volatile
    private var previousCliPath: String = pluginSettings().cliPath

    private fun runPostApplySettings() {
        val settings = pluginSettings()
        // Use runInBackground directly (not wrapped in runAsync) to ensure the task executes
        runInBackground("Snyk: applying settings") {
            // Handle release channel change - prompt to download new CLI
            if (settings.cliReleaseChannel.isNotBlank() && previousReleaseChannel.isNotBlank() && settings.cliReleaseChannel != previousReleaseChannel) {
                handleReleaseChannelChange(project)
            }

            // Handle delta findings change - clear caches
            if (settings.isDeltaFindingsEnabled() != previousDeltaEnabled) {
                handleDeltaFindingsChange(project)
            }

            // Handle CLI-related changes that require LS restart
            val manageBinariesToggled = settings.manageBinariesAutomatically != previousManageBinariesAutomatically
            val cliPathChanged = settings.cliPath != previousCliPath

            if (manageBinariesToggled || cliPathChanged) {
                // Download latest release if necessary, then restart
                getSnykTaskQueueService(project)?.downloadLatestRelease(force = true, forceRestart = true)
            }

            executePostApplySettings(project)
        }
    }

    override fun dispose() {
        isDisposed = true
        disposeCurrentBrowser()
    }
}
