package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.runInBackground
import java.io.File.separator
import io.snyk.plugin.services.AuthenticationType
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.trust.WorkspaceTrustService
import java.nio.file.Paths

class SaveConfigHandler(
    private val project: Project,
    private val onModified: () -> Unit,
    private val onSaveComplete: (() -> Unit)? = null
) {
    private val logger = Logger.getInstance(SaveConfigHandler::class.java)
    private val gson = Gson()

    fun generateSaveConfigHandler(
        jbCefBrowser: JBCefBrowserBase,
        getThemeCss: (() -> String)? = null,
        nonce: String? = null
    ): CefLoadHandlerAdapter {
        val saveConfigQuery = JBCefJSQuery.create(jbCefBrowser)
        val loginQuery = JBCefJSQuery.create(jbCefBrowser)
        val logoutQuery = JBCefJSQuery.create(jbCefBrowser)
        val onFormDirtyChangeQuery = JBCefJSQuery.create(jbCefBrowser)

        saveConfigQuery.addHandler { jsonString ->
            try {
                parseAndSaveConfig(jsonString)
                // Hide any previous error on success
                jbCefBrowser.cefBrowser.executeJavaScript(
                    "if (typeof window.hideError === 'function') { window.hideError(); }",
                    jbCefBrowser.cefBrowser.url, 0
                )
                // Notify that save is complete (for post-apply logic)
                onSaveComplete?.invoke()
                JBCefJSQuery.Response("success")
            } catch (e: Exception) {
                logger.warn("Error saving config", e)
                // Show error in browser
                val errorMsg = (e.message ?: "Unknown error").replace("'", "\\'")
                jbCefBrowser.cefBrowser.executeJavaScript(
                    "if (typeof window.showError === 'function') { window.showError('$errorMsg'); }",
                    jbCefBrowser.cefBrowser.url, 0
                )
                JBCefJSQuery.Response(null, 1, e.message ?: "Unknown error")
            }
        }

        // Handle dirty state changes from LS DirtyTracker and fallback HTML
        onFormDirtyChangeQuery.addHandler { isDirtyStr ->
            val isDirty = isDirtyStr == "true"
            if (isDirty) {
                onModified()
            }
            JBCefJSQuery.Response("success")
        }

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
                    // Inject IDE theme CSS variables
                    val themeCss = try {
                        getThemeCss?.invoke()
                    } catch (e: Exception) {
                        logger.warn("Error getting theme CSS", e)
                        null
                    }
                    if (!themeCss.isNullOrBlank()) {
                        logger.debug("Injecting theme CSS (${themeCss.length} chars), nonce: ${nonce?.take(8)}...")
                        val nonceAttr = if (!nonce.isNullOrBlank()) "style.setAttribute('nonce', ${gson.toJson(nonce)});" else ""
                        val cssScript = """
                        (function() {
                            var style = document.createElement('style');
                            $nonceAttr
                            style.textContent = ${gson.toJson(themeCss)};
                            document.head.appendChild(style);
                            console.log('IDE theme CSS injected');
                        })();
                        """
                        browser.executeJavaScript(cssScript, browser.url, 0)
                    } else {
                        logger.warn("Theme CSS is null or blank, skipping injection")
                    }

                    // Inject IDE bridge functions
                    val script = """
                    (function() {
                        if (window.__saveIdeConfig__) {
                            return;
                        }
                        window.__saveIdeConfig__ = function(value) { ${saveConfigQuery.inject("value")} };
                        window.__onFormDirtyChange__ = function(isDirty) { ${onFormDirtyChangeQuery.inject("String(isDirty)")} };
                        window.__ideLogin__ = function() { ${loginQuery.inject("'login'")} };
                        window.__ideLogout__ = function() { ${logoutQuery.inject("'logout'")} };
                    })();
                    """
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }

    private fun parseAndSaveConfig(jsonString: String) {
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val config: Map<String, Any?> = try {
            gson.fromJson(jsonString, type)
        } catch (e: JsonSyntaxException) {
            logger.warn("Invalid JSON config received: ${jsonString.take(200)}", e)
            throw IllegalArgumentException("Invalid configuration format", e)
        } ?: emptyMap()

        val settings = pluginSettings()
        applyGlobalSettings(config, settings)
        applyFolderConfigs(config)
    }

    /**
     * Safely extracts a Boolean value from a config map, handling various input types.
     * Supports Boolean, String ("true"/"false"), and Number (0/non-zero) values.
     * Returns the default for missing keys (HTML forms omit unchecked checkboxes).
     */
    private fun Map<String, Any?>.getBoolean(key: String, default: Boolean = false): Boolean {
        return when (val value = this[key]) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            is Number -> value.toInt() != 0
            else -> default
        }
    }

    /**
     * Safely extracts a String value from a config map.
     */
    private fun Map<String, Any?>.getString(key: String): String? {
        return this[key] as? String
    }

    private fun applyGlobalSettings(config: Map<String, Any?>, settings: SnykApplicationSettingsStateService) {
        // Scan type toggles - only apply if any scan type key is present (supports partial configs)
        // Missing keys treated as false (unchecked checkbox) when the form has scan types
        val hasScanTypeKeys = config.containsKey("activateSnykOpenSource") ||
                               config.containsKey("activateSnykCode") ||
                               config.containsKey("activateSnykIac")
        if (hasScanTypeKeys) {
            settings.ossScanEnable = config.getBoolean("activateSnykOpenSource")
            settings.snykCodeSecurityIssuesScanEnable = config.getBoolean("activateSnykCode")
            settings.iacScanEnabled = config.getBoolean("activateSnykIac")
        }

        // Scanning mode
        config.getString("scanningMode")?.let { settings.scanOnSave = (it == "auto") }

        // Connection settings
        config.getString("organization")?.let { settings.organization = it }
        config.getString("endpoint")?.let { settings.customEndpointUrl = it }
        config.getString("token")?.let { settings.token = it }
        if (config.containsKey("insecure")) {
            settings.ignoreUnknownCA = config.getBoolean("insecure")
        }

        // Authentication method
        config.getString("authenticationMethod")?.let { method ->
            settings.authenticationType = when (method) {
                "oauth" -> AuthenticationType.OAUTH2
                "token" -> AuthenticationType.API_TOKEN
                "pat" -> AuthenticationType.PAT
                else -> AuthenticationType.OAUTH2
            }
        }

        // Severity filters - only apply if section is present
        @Suppress("UNCHECKED_CAST")
        (config["filterSeverity"] as? Map<String, Any?>)?.let { severity ->
            settings.criticalSeverityEnabled = severity.getBoolean("critical")
            settings.highSeverityEnabled = severity.getBoolean("high")
            settings.mediumSeverityEnabled = severity.getBoolean("medium")
            settings.lowSeverityEnabled = severity.getBoolean("low")
        }

        // Issue view options - only apply if section is present
        @Suppress("UNCHECKED_CAST")
        (config["issueViewOptions"] as? Map<String, Any?>)?.let { options ->
            settings.openIssuesEnabled = options.getBoolean("openIssues")
            settings.ignoredIssuesEnabled = options.getBoolean("ignoredIssues")
        }

        // Delta findings - only apply if key is present
        if (config.containsKey("enableDeltaFindings")) {
            settings.setDeltaEnabled(config.getBoolean("enableDeltaFindings"))
        }

        // CLI settings
        config.getString("cliPath")?.let { path ->
            settings.cliPath = path.ifEmpty { getPluginPath() + separator + Platform.current().snykWrapperFileName }
        }
        if (config.containsKey("manageBinariesAutomatically")) {
            settings.manageBinariesAutomatically = config.getBoolean("manageBinariesAutomatically")
        }
        config.getString("baseUrl")?.takeIf { it.isNotEmpty() }?.let { settings.cliBaseDownloadURL = it }
        config.getString("cliBaseDownloadURL")?.takeIf { it.isNotEmpty() }?.let { settings.cliBaseDownloadURL = it }
        config.getString("cliReleaseChannel")?.takeIf { it.isNotEmpty() }?.let { settings.cliReleaseChannel = it }

        // Trusted folders
        @Suppress("UNCHECKED_CAST")
        (config["trustedFolders"] as? List<String>)?.let { folders ->
            val trustService = service<WorkspaceTrustService>()
            folders.forEach { folder ->
                try {
                    trustService.addTrustedPath(Paths.get(folder))
                } catch (e: Exception) {
                    logger.warn("Failed to add trusted folder: $folder", e)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyFolderConfigs(config: Map<String, Any?>) {
        val folderConfigs = config["folderConfigs"] as? List<Map<String, Any?>> ?: return
        val fcs = service<FolderConfigSettings>()

        for (folderConfig in folderConfigs) {
            val folderPath = folderConfig["folderPath"] as? String ?: continue
            val existingConfig = fcs.getFolderConfig(folderPath)

            val additionalParams = (folderConfig["additionalParameters"] as? String)
                ?.split(" ", System.lineSeparator())
                ?.filter { it.isNotBlank() }
                ?: existingConfig.additionalParameters

            val orgSetByUser = folderConfig.getBoolean("orgSetByUser", existingConfig.orgSetByUser)

            val preferredOrg = if (orgSetByUser) {
                (folderConfig["preferredOrg"] as? String) ?: existingConfig.preferredOrg
            } else {
                ""
            }

            // Parse scanCommandConfig if present
            val scanCommandConfig = parseScanCommandConfig(folderConfig)

            val updatedConfig = existingConfig.copy(
                additionalParameters = additionalParams,
                preferredOrg = preferredOrg,
                orgSetByUser = orgSetByUser,
                scanCommandConfig = scanCommandConfig ?: existingConfig.scanCommandConfig
            )
            fcs.addFolderConfig(updatedConfig)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseScanCommandConfig(folderConfig: Map<String, Any?>): Map<String, snyk.common.lsp.ScanCommandConfig>? {
        val scanConfig = folderConfig["scanCommandConfig"] as? Map<String, Map<String, Any?>> ?: return null

        val result = mutableMapOf<String, snyk.common.lsp.ScanCommandConfig>()

        for ((product, config) in scanConfig) {
            if (config.isEmpty()) continue

            result[product] = snyk.common.lsp.ScanCommandConfig(
                preScanCommand = (config["command"] as? String) ?: "",
                preScanOnlyReferenceFolder = config.getBoolean("preScanOnlyReferenceFolder", true),
                postScanCommand = (config["postScanCommand"] as? String) ?: "",
                postScanOnlyReferenceFolder = config.getBoolean("postScanOnlyReferenceFolder", true)
            )
        }

        return if (result.isNotEmpty()) result else null
    }
}
