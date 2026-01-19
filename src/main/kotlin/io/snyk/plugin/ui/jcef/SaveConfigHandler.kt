package io.snyk.plugin.ui.jcef

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.runInBackground
import io.snyk.plugin.services.AuthenticationType
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.trust.WorkspaceTrustService
import java.io.File.separator
import java.nio.file.Paths

class SaveConfigHandler(
    private val project: Project,
    private val onModified: () -> Unit,
    private val onReset: (() -> Unit)? = null,
    private val onSaveComplete: (() -> Unit)? = null
) {
    private val logger = Logger.getInstance(SaveConfigHandler::class.java)
    private val gson = GsonBuilder().create()

    fun generateSaveConfigHandler(
        jbCefBrowser: JBCefBrowserBase,
        nonce: String? = null
    ): CefLoadHandlerAdapter {
        val saveConfigQuery = JBCefJSQuery.create(jbCefBrowser)
        val saveAttemptFinishedQuery = JBCefJSQuery.create(jbCefBrowser)
        val loginQuery = JBCefJSQuery.create(jbCefBrowser)
        val logoutQuery = JBCefJSQuery.create(jbCefBrowser)
        val onFormDirtyChangeQuery = JBCefJSQuery.create(jbCefBrowser)

        saveConfigQuery.addHandler { jsonString ->
            var response: JBCefJSQuery.Response
            try {
                saveConfig(jsonString)
                // Hide any previous error on success - defer to avoid EDT blocking
                invokeLater {
                    jbCefBrowser.cefBrowser.executeJavaScript(
                        "if (typeof window.hideError === 'function') { window.hideError(); }",
                        jbCefBrowser.cefBrowser.url, 0
                    )
                }
                response = JBCefJSQuery.Response("success")
            } catch (e: Exception) {
                logger.warn("Error saving config", e)
                // Show error in browser - defer to avoid EDT blocking
                val errorMsg = (e.message ?: "Unknown error").replace("'", "\\'")
                invokeLater {
                    jbCefBrowser.cefBrowser.executeJavaScript(
                        "if (typeof window.showError === 'function') { window.showError('$errorMsg'); }",
                        jbCefBrowser.cefBrowser.url, 0
                    )
                }
                response = JBCefJSQuery.Response(null, 1, e.message ?: "Unknown error")
            } finally {
                try {
                    onSaveComplete?.invoke()
                } catch (e: Exception) {
                    logger.warn("Error in onSaveComplete callback", e)
                }
            }
            response
        }

        saveAttemptFinishedQuery.addHandler { status ->
            // Only invoke onSaveComplete for non-success statuses.
            // For success, onSaveComplete is already called by saveConfigQuery handler.
            if (status != "success") {
                try {
                    onSaveComplete?.invoke()
                } catch (e: Exception) {
                    logger.warn("Error in onSaveComplete callback", e)
                }
            }
            JBCefJSQuery.Response("success")
        }

        // Handle dirty state changes from LS DirtyTracker and fallback HTML
        onFormDirtyChangeQuery.addHandler { isDirtyStr ->
            val isDirty = isDirtyStr == "true"
            if (isDirty) {
                onModified()
            } else {
                onReset?.invoke()
            }
            JBCefJSQuery.Response("success")
        }

        // Subscribe to settings changes to update auth token in browser
        project.messageBus.connect().subscribe(
            SnykSettingsListener.SNYK_SETTINGS_TOPIC,
            object : SnykSettingsListener {
                override fun settingsChanged() {
                    // Defer JavaScript execution to avoid EDT blocking
                    invokeLater {
                        val token = pluginSettings().token
                        if (token?.isNotEmpty() == true) {
                            val escapedToken = token.replace("\\", "\\\\").replace("'", "\\'")
                            jbCefBrowser.cefBrowser.executeJavaScript(
                                "if (typeof window.setAuthToken === 'function') { window.setAuthToken('$escapedToken'); }",
                                jbCefBrowser.cefBrowser.url, 0
                            )
                        } else {
                            jbCefBrowser.cefBrowser.executeJavaScript(
                                "if (typeof window.setAuthToken === 'function') { window.setAuthToken(''); }",
                                jbCefBrowser.cefBrowser.url, 0
                            )
                        }
                    }
                }
            }
        )

        loginQuery.addHandler {
            // Don't use runInBackground - authenticate() handles its own threading
            getSnykCliAuthenticationService(project)?.authenticate()
            JBCefJSQuery.Response("success")
        }

        logoutQuery.addHandler {
            runInBackground("Snyk: logging out...") {
                pluginSettings().token = ""
                LanguageServerWrapper.getInstance(project).logout()
            }
            JBCefJSQuery.Response("success")
        }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    // Inject IDE bridge functions
                    val script = """
                    (function() {
                        if (typeof window.__saveIdeConfig__ !== 'function') {
                            window.__saveIdeConfig__ = function(value) { ${saveConfigQuery.inject("value")} };
                        }
                        if (typeof window.__ideSaveAttemptFinished__ !== 'function') {
                            window.__ideSaveAttemptFinished__ = function(status) { ${saveAttemptFinishedQuery.inject("String(status)")} };
                        }
                        if (typeof window.__onFormDirtyChange__ !== 'function') {
                            window.__onFormDirtyChange__ = function(isDirty) { ${onFormDirtyChangeQuery.inject("String(isDirty)")} };
                        }
                        if (typeof window.__ideLogin__ !== 'function') {
                            window.__ideLogin__ = function() { ${loginQuery.inject("'login'")} };
                        }
                        if (typeof window.__ideLogout__ !== 'function') {
                            window.__ideLogout__ = function() { ${logoutQuery.inject("'logout'")} };
                        }
                    })();
                    """
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }

    private fun saveConfig(jsonString: String) {
        val config: SaveConfigRequest = try {
            gson.fromJson(jsonString, SaveConfigRequest::class.java)
        } catch (e: JsonSyntaxException) {
            logger.warn("Invalid JSON config received: ${jsonString.take(200)}", e)
            throw IllegalArgumentException("Invalid configuration format", e)
        }

        val settings = pluginSettings()
        applyGlobalSettings(config, settings)

        // Only apply folder configs if not fallback form
        val isFallback = config.isFallbackForm == true
        if (!isFallback) {
            config.folderConfigs?.let { applyFolderConfigs(it) }
        }
    }

    private fun applyGlobalSettings(config: SaveConfigRequest, settings: SnykApplicationSettingsStateService) {
        val isFallback = config.isFallbackForm == true

        // CLI Settings - always persist for both fallback and full forms
        config.cliPath?.let { path ->
            settings.cliPath = path.ifEmpty { getPluginPath() + separator + Platform.current().snykWrapperFileName }
        }
        config.manageBinariesAutomatically?.let { settings.manageBinariesAutomatically = it }
        config.cliBaseDownloadURL?.let { settings.cliBaseDownloadURL = it }
        config.cliReleaseChannel?.let { settings.cliReleaseChannel = it }
        config.insecure?.let { settings.ignoreUnknownCA = it }

        if (!isFallback) {
            settings.ossScanEnable = config.activateSnykOpenSource ?: false
            settings.snykCodeSecurityIssuesScanEnable = config.activateSnykCode ?: false
            settings.iacScanEnabled = config.activateSnykIac ?: false

            // Scanning mode
            config.scanningMode?.let { settings.scanOnSave = (it == "auto") }

            // Connection settings
            config.organization?.let { settings.organization = it }
            config.endpoint?.let { settings.customEndpointUrl = it }
            config.token?.let { settings.token = it }

            // Authentication method
            config.authenticationMethod?.let { method ->
                settings.authenticationType = when (method) {
                    "oauth" -> AuthenticationType.OAUTH2
                    "token" -> AuthenticationType.API_TOKEN
                    "pat" -> AuthenticationType.PAT
                    else -> AuthenticationType.OAUTH2
                }
            }

            // Severity filters
            config.filterSeverity?.let { severity ->
                severity.critical?.let { settings.criticalSeverityEnabled = it }
                severity.high?.let { settings.highSeverityEnabled = it }
                severity.medium?.let { settings.mediumSeverityEnabled = it }
                severity.low?.let { settings.lowSeverityEnabled = it }
            }

            // Issue view options
            config.issueViewOptions?.let { options ->
                options.openIssues?.let { settings.openIssuesEnabled = it }
                options.ignoredIssues?.let { settings.ignoredIssuesEnabled = it }
            }

            // Delta findings
            config.enableDeltaFindings?.let { settings.setDeltaEnabled(it) }

            // Risk score threshold
            config.riskScoreThreshold?.let { settings.riskScoreThreshold = it }

            // Trusted folders - sync the list (add new, remove missing)
            config.trustedFolders?.let { folders ->
                val trustService = service<WorkspaceTrustService>()
                val configPaths = folders.mapNotNull { folder ->
                    try {
                        Paths.get(folder)
                    } catch (e: Exception) {
                        logger.warn("Invalid path in trusted folders: $folder", e)
                        null
                    }
                }.toSet()

                val currentPaths = trustService.settings.getTrustedPaths().mapNotNull { pathStr ->
                    try {
                        Paths.get(pathStr)
                    } catch (e: Exception) {
                        logger.warn("Invalid path in current trusted paths: $pathStr", e)
                        null
                    }
                }.toSet()

                // Remove paths that are no longer in the config
                currentPaths.forEach { currentPath ->
                    if (currentPath !in configPaths) {
                        try {
                            trustService.removeTrustedPath(currentPath)
                        } catch (e: Exception) {
                            logger.warn("Failed to remove trusted folder: $currentPath", e)
                        }
                    }
                }

                // Add new paths from the config
                configPaths.forEach { configPath ->
                    try {
                        trustService.addTrustedPath(configPath)
                    } catch (e: Exception) {
                        logger.warn("Failed to add trusted folder: $configPath", e)
                    }
                }
            }
        }
    }

    private fun applyFolderConfigs(folderConfigs: List<FolderConfigData>) {
        val fcs = service<FolderConfigSettings>()

        for (folderConfig in folderConfigs) {
            val existingConfig = fcs.getFolderConfig(folderConfig.folderPath)

            // Build updated config, writing values directly from config (use defaults if null)
            val updatedConfig = existingConfig.copy(
                additionalParameters = folderConfig.additionalParameters,
                additionalEnv = folderConfig.additionalEnv,
                preferredOrg = folderConfig.preferredOrg ?: "",
                autoDeterminedOrg = folderConfig.autoDeterminedOrg ?: "",
                orgSetByUser = folderConfig.orgSetByUser ?: false,
                scanCommandConfig = folderConfig.scanCommandConfig?.let { parseScanCommandConfig(it) }
                    ?: existingConfig.scanCommandConfig
            )
            fcs.addFolderConfig(updatedConfig)
        }
    }

    private fun parseScanCommandConfig(scanConfig: Map<String, ScanCommandConfigData>): Map<String, snyk.common.lsp.ScanCommandConfig> {
        val result = mutableMapOf<String, snyk.common.lsp.ScanCommandConfig>()

        for ((product, config) in scanConfig) {
            result[product] = snyk.common.lsp.ScanCommandConfig(
                preScanCommand = config.preScanCommand ?: "",
                preScanOnlyReferenceFolder = config.preScanOnlyReferenceFolder ?: false,
                postScanCommand = config.postScanCommand ?: "",
                postScanOnlyReferenceFolder = config.postScanOnlyReferenceFolder ?: false
            )
        }

        return result
    }
}
