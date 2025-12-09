package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
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
    private val onConfigChanged: () -> Unit
) {
    private val logger = Logger.getInstance(SaveConfigHandler::class.java)
    private val gson = Gson()

    fun generateSaveConfigHandler(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val saveConfigQuery = JBCefJSQuery.create(jbCefBrowser)
        val loginQuery = JBCefJSQuery.create(jbCefBrowser)
        val logoutQuery = JBCefJSQuery.create(jbCefBrowser)

        saveConfigQuery.addHandler { jsonString ->
            try {
                parseAndSaveConfig(jsonString)
                onConfigChanged()
                runInBackground("Snyk: updating configuration...") {
                    LanguageServerWrapper.getInstance(project).updateConfiguration(true)
                }
                JBCefJSQuery.Response("success")
            } catch (e: Exception) {
                logger.warn("Error saving config", e)
                JBCefJSQuery.Response(null, 1, e.message ?: "Unknown error")
            }
        }

        loginQuery.addHandler {
            runInBackground("Snyk: authenticating...") {
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
        val config: Map<String, Any?> = gson.fromJson(jsonString, type)
        val settings = pluginSettings()

        applyGlobalSettings(config, settings)
        applyFolderConfigs(config)
    }

    private fun applyGlobalSettings(config: Map<String, Any?>, settings: SnykApplicationSettingsStateService) {
        (config["activateSnykOpenSource"] as? Boolean)?.let { settings.ossScanEnable = it }
        (config["activateSnykCode"] as? Boolean)?.let { settings.snykCodeSecurityIssuesScanEnable = it }
        (config["activateSnykIac"] as? Boolean)?.let { settings.iacScanEnabled = it }

        (config["scanningMode"] as? String)?.let {
            settings.scanOnSave = (it == "auto")
        }

        (config["organization"] as? String)?.let { settings.organization = it }
        (config["endpoint"] as? String)?.let { settings.customEndpointUrl = it }
        (config["token"] as? String)?.let { settings.token = it }
        (config["insecure"] as? Boolean)?.let { settings.ignoreUnknownCA = it }

        (config["authenticationMethod"] as? String)?.let { method ->
            settings.authenticationType = when (method) {
                "oauth" -> AuthenticationType.OAUTH2
                "token" -> AuthenticationType.API_TOKEN
                else -> AuthenticationType.OAUTH2
            }
        }

        @Suppress("UNCHECKED_CAST")
        (config["filterSeverity"] as? Map<String, Boolean>)?.let { severity ->
            severity["critical"]?.let { settings.criticalSeverityEnabled = it }
            severity["high"]?.let { settings.highSeverityEnabled = it }
            severity["medium"]?.let { settings.mediumSeverityEnabled = it }
            severity["low"]?.let { settings.lowSeverityEnabled = it }
        }

        @Suppress("UNCHECKED_CAST")
        (config["issueViewOptions"] as? Map<String, Boolean>)?.let { options ->
            options["openIssues"]?.let { settings.openIssuesEnabled = it }
            options["ignoredIssues"]?.let { settings.ignoredIssuesEnabled = it }
        }

        (config["enableDeltaFindings"] as? String)?.let {
            settings.setDeltaEnabled(it == "true")
        }

        (config["cliPath"] as? String)?.let { settings.cliPath = it }
        (config["manageBinariesAutomatically"] as? Boolean)?.let { settings.manageBinariesAutomatically = it }
        (config["cliBaseDownloadURL"] as? String)?.let { settings.cliBaseDownloadURL = it }
        (config["cliReleaseChannel"] as? String)?.let { settings.cliReleaseChannel = it }
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

            val orgSetByUser = when (val value = folderConfig["orgSetByUser"]) {
                is Boolean -> value
                is String -> value == "true"
                else -> existingConfig.orgSetByUser
            }

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
