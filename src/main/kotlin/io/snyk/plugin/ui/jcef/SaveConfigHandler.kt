package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
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

class SaveConfigHandler(
    private val project: Project,
    private val onModified: () -> Unit
) {
    private val logger = Logger.getInstance(SaveConfigHandler::class.java)
    private val gson = Gson()

    fun generateSaveConfigHandler(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val saveConfigQuery = JBCefJSQuery.create(jbCefBrowser)
        val loginQuery = JBCefJSQuery.create(jbCefBrowser)
        val logoutQuery = JBCefJSQuery.create(jbCefBrowser)
        val notifyModifiedQuery = JBCefJSQuery.create(jbCefBrowser)

        saveConfigQuery.addHandler { jsonString ->
            try {
                parseAndSaveConfig(jsonString)
                JBCefJSQuery.Response("success")
            } catch (e: Exception) {
                logger.warn("Error saving config", e)
                JBCefJSQuery.Response(null, 1, e.message ?: "Unknown error")
            }
        }

        notifyModifiedQuery.addHandler {
            onModified()
            JBCefJSQuery.Response("success")
        }

        loginQuery.addHandler { jsonConfig ->
            // Save config first (like the old dialog does before authentication)
            try {
                if (jsonConfig.isNotBlank() && jsonConfig != "login") {
                    parseAndSaveConfig(jsonConfig)
                }
            } catch (e: Exception) {
                logger.warn("Error saving config before login", e)
            }
            runInBackground("Snyk: authenticating...") {
                LanguageServerWrapper.getInstance(project).updateConfiguration(true)
                getSnykCliAuthenticationService(project)?.authenticate()
            }
            JBCefJSQuery.Response("success")
        }

        logoutQuery.addHandler {
            runInBackground("Snyk: logging out...") {
                LanguageServerWrapper.getInstance(project).logout()
            }
            JBCefJSQuery.Response("success")
        }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    val script = """
                    (function() {
                        if (window.__ideSaveConfig__) {
                            return;
                        }
                        window.__ideSaveConfig__ = function(value) { ${saveConfigQuery.inject("value")} };
                        window.__ideNotifyModified__ = function() { ${notifyModifiedQuery.inject("'modified'")} };
                        window.__ideLogin__ = function(configJson) { ${loginQuery.inject("configJson || 'login'")} };
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
     */
    private fun Map<String, Any?>.getBoolean(key: String, default: Boolean? = null): Boolean? {
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
        config.getBoolean("activateSnykOpenSource")?.let { settings.ossScanEnable = it }
        config.getBoolean("activateSnykCode")?.let { settings.snykCodeSecurityIssuesScanEnable = it }
        config.getBoolean("activateSnykIac")?.let { settings.iacScanEnabled = it }

        config.getString("scanningMode")?.let {
            settings.scanOnSave = (it == "auto")
        }

        config.getString("organization")?.let { settings.organization = it }
        config.getString("endpoint")?.let { settings.customEndpointUrl = it }
        config.getString("token")?.let { settings.token = it }
        config.getBoolean("insecure")?.let { settings.ignoreUnknownCA = it }

        config.getString("authenticationMethod")?.let { method ->
            settings.authenticationType = when (method) {
                "oauth" -> AuthenticationType.OAUTH2
                "token" -> AuthenticationType.API_TOKEN
                else -> AuthenticationType.OAUTH2
            }
        }

        @Suppress("UNCHECKED_CAST")
        (config["filterSeverity"] as? Map<String, Any?>)?.let { severity ->
            severity.getBoolean("critical")?.let { settings.criticalSeverityEnabled = it }
            severity.getBoolean("high")?.let { settings.highSeverityEnabled = it }
            severity.getBoolean("medium")?.let { settings.mediumSeverityEnabled = it }
            severity.getBoolean("low")?.let { settings.lowSeverityEnabled = it }
        }

        @Suppress("UNCHECKED_CAST")
        (config["issueViewOptions"] as? Map<String, Any?>)?.let { options ->
            options.getBoolean("openIssues")?.let { settings.openIssuesEnabled = it }
            options.getBoolean("ignoredIssues")?.let { settings.ignoredIssuesEnabled = it }
        }

        config.getBoolean("enableDeltaFindings")?.let {
            settings.setDeltaEnabled(it)
        }

        config.getString("cliPath")?.let { settings.cliPath = it }
        config.getBoolean("manageBinariesAutomatically")?.let { settings.manageBinariesAutomatically = it }
        config.getString("cliBaseDownloadURL")?.let { settings.cliBaseDownloadURL = it }
        config.getString("cliReleaseChannel")?.let { settings.cliReleaseChannel = it }
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
                ?: existingConfig.orgSetByUser

            val preferredOrg = if (orgSetByUser) {
                (folderConfig["preferredOrg"] as? String) ?: existingConfig.preferredOrg
            } else {
                ""
            }

            val updatedConfig = existingConfig.copy(
                additionalParameters = additionalParams,
                preferredOrg = preferredOrg,
                orgSetByUser = orgSetByUser
            )
            fcs.addFolderConfig(updatedConfig)
        }
    }
}
