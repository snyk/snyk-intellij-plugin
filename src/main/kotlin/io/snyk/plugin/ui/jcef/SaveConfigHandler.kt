package io.snyk.plugin.ui.jcef

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getDefaultCliPath
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.runInBackground
import io.snyk.plugin.services.AuthenticationType
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import java.nio.file.Paths
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LsFolderSettingsKeys
import snyk.common.lsp.settings.LsSettingsKeys
import snyk.common.lsp.settings.withSetting
import snyk.trust.WorkspaceTrustService

class SaveConfigHandler(
  private val project: Project,
  private val onModified: () -> Unit,
  private val onReset: (() -> Unit)? = null,
  private val onSaveComplete: (() -> Unit)? = null,
) {
  private val logger = Logger.getInstance(SaveConfigHandler::class.java)
  private val gson = GsonBuilder().create()

  fun generateSaveConfigHandler(
    jbCefBrowser: JBCefBrowserBase,
    nonce: String? = null,
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
            jbCefBrowser.cefBrowser.url,
            0,
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
            jbCefBrowser.cefBrowser.url,
            0,
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
    project.messageBus
      .connect()
      .subscribe(
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
                  jbCefBrowser.cefBrowser.url,
                  0,
                )
              } else {
                jbCefBrowser.cefBrowser.executeJavaScript(
                  "if (typeof window.setAuthToken === 'function') { window.setAuthToken(''); }",
                  jbCefBrowser.cefBrowser.url,
                  0,
                )
              }
            }
          }
        },
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
          val script =
            """
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
    val config: SaveConfigRequest =
      try {
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

  private fun applyGlobalSettings(
    config: SaveConfigRequest,
    settings: SnykApplicationSettingsStateService,
  ) {
    val isFallback = config.isFallbackForm == true

    config.manageBinariesAutomatically?.let {
      settings.manageBinariesAutomatically = it
      settings.markExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
    }

    // Use the provided cliPath from the config if present, or the default CLI path if not.
    config.cliPath?.let { path ->
      settings.cliPath = path.ifEmpty { getDefaultCliPath() }
      settings.markExplicitlyChanged(LsSettingsKeys.CLI_PATH)
    }

    config.cliBaseDownloadURL?.let {
      settings.cliBaseDownloadURL = it
      settings.markExplicitlyChanged(LsSettingsKeys.BINARY_BASE_URL)
    }
    config.cliReleaseChannel?.let { settings.cliReleaseChannel = it }
    config.insecure?.let {
      settings.ignoreUnknownCA = it
      settings.markExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE)
    }

    if (!isFallback) {
      settings.ossScanEnable = config.activateSnykOpenSource ?: false
      settings.markExplicitlyChanged(LsSettingsKeys.SNYK_OSS_ENABLED)
      settings.snykCodeSecurityIssuesScanEnable = config.activateSnykCode ?: false
      settings.markExplicitlyChanged(LsSettingsKeys.SNYK_CODE_ENABLED)
      settings.iacScanEnabled = config.activateSnykIac ?: false
      settings.markExplicitlyChanged(LsSettingsKeys.SNYK_IAC_ENABLED)
      settings.secretsEnabled = config.activateSnykSecrets ?: false
      settings.markExplicitlyChanged(LsSettingsKeys.SNYK_SECRETS_ENABLED)

      // Scanning mode
      config.scanningMode?.let {
        settings.scanOnSave = (it == "auto")
        settings.markExplicitlyChanged(LsSettingsKeys.SCAN_AUTOMATIC)
      }

      // Connection settings
      config.organization?.let {
        settings.organization = it
        settings.markExplicitlyChanged(LsSettingsKeys.ORGANIZATION)
      }
      config.endpoint?.let {
        settings.customEndpointUrl = it
        settings.markExplicitlyChanged(LsSettingsKeys.API_ENDPOINT)
      }
      config.token?.let {
        settings.token = it
        settings.markExplicitlyChanged(LsSettingsKeys.TOKEN)
      }

      // Authentication method
      config.authenticationMethod?.let { method ->
        settings.authenticationType =
          when (method) {
            "oauth" -> AuthenticationType.OAUTH2
            "token" -> AuthenticationType.API_TOKEN
            "pat" -> AuthenticationType.PAT
            else -> AuthenticationType.OAUTH2
          }
        settings.markExplicitlyChanged(LsSettingsKeys.AUTHENTICATION_METHOD)
      }

      // Severity filters
      config.filterSeverity?.let { severity ->
        severity.critical?.let {
          settings.criticalSeverityEnabled = it
          settings.markExplicitlyChanged(LsSettingsKeys.ENABLED_SEVERITIES)
        }
        severity.high?.let {
          settings.highSeverityEnabled = it
          settings.markExplicitlyChanged(LsSettingsKeys.ENABLED_SEVERITIES)
        }
        severity.medium?.let {
          settings.mediumSeverityEnabled = it
          settings.markExplicitlyChanged(LsSettingsKeys.ENABLED_SEVERITIES)
        }
        severity.low?.let {
          settings.lowSeverityEnabled = it
          settings.markExplicitlyChanged(LsSettingsKeys.ENABLED_SEVERITIES)
        }
      }

      // Issue view options
      config.issueViewOptions?.let { options ->
        options.openIssues?.let {
          settings.openIssuesEnabled = it
          settings.markExplicitlyChanged(LsSettingsKeys.ISSUE_VIEW_OPEN_ISSUES)
        }
        options.ignoredIssues?.let {
          settings.ignoredIssuesEnabled = it
          settings.markExplicitlyChanged(LsSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES)
        }
      }

      // Delta findings
      config.enableDeltaFindings?.let {
        settings.setDeltaEnabled(it)
        settings.markExplicitlyChanged(LsSettingsKeys.SCAN_NET_NEW)
      }

      // Risk score threshold
      config.riskScoreThreshold?.let {
        settings.riskScoreThreshold = it
        settings.markExplicitlyChanged(LsSettingsKeys.RISK_SCORE_THRESHOLD)
      }

      // Trusted folders - sync the list (add new, remove missing)
      config.trustedFolders?.let { folders ->
        val trustService = service<WorkspaceTrustService>()
        val configPaths =
          folders
            .mapNotNull { folder ->
              try {
                Paths.get(folder)
              } catch (e: Exception) {
                logger.warn("Invalid path in trusted folders: $folder", e)
                null
              }
            }
            .toSet()

        val currentPaths =
          trustService.settings
            .getTrustedPaths()
            .mapNotNull { pathStr ->
              try {
                Paths.get(pathStr)
              } catch (e: Exception) {
                logger.warn("Invalid path in current trusted paths: $pathStr", e)
                null
              }
            }
            .toSet()

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
      var updated = fcs.getFolderConfig(folderConfig.folderPath)

      // Apply each field from the UI form, marking as changed
      folderConfig.additionalParameters?.let {
        updated =
          updated.withSetting(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS, it, changed = true)
      }
      folderConfig.additionalEnv?.let {
        updated =
          updated.withSetting(LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT, it, changed = true)
      }
      folderConfig.preferredOrg?.let {
        updated = updated.withSetting(LsFolderSettingsKeys.PREFERRED_ORG, it, changed = true)
      }
      folderConfig.autoDeterminedOrg?.let {
        updated = updated.withSetting(LsFolderSettingsKeys.AUTO_DETERMINED_ORG, it)
      }
      folderConfig.orgSetByUser?.let {
        updated = updated.withSetting(LsFolderSettingsKeys.ORG_SET_BY_USER, it, changed = true)
      }
      folderConfig.scanCommandConfig?.let {
        updated =
          updated.withSetting(
            LsFolderSettingsKeys.SCAN_COMMAND_CONFIG,
            parseScanCommandConfig(it),
            changed = true,
          )
      }

      // Org-scope overrides from processFolderOverrides() in JS
      folderConfig.scanAutomatic?.let {
        updated = updated.withSetting(LsSettingsKeys.SCAN_AUTOMATIC, it, changed = true)
      }
      folderConfig.scanNetNew?.let {
        updated = updated.withSetting(LsSettingsKeys.SCAN_NET_NEW, it, changed = true)
      }
      folderConfig.enabledSeverities?.let {
        val sev =
          snyk.common.lsp.settings.SeverityFilter(
            critical = it.critical,
            high = it.high,
            medium = it.medium,
            low = it.low,
          )
        updated = updated.withSetting(LsSettingsKeys.ENABLED_SEVERITIES, sev, changed = true)
      }
      folderConfig.snykOssEnabled?.let {
        updated = updated.withSetting(LsSettingsKeys.SNYK_OSS_ENABLED, it, changed = true)
      }
      folderConfig.snykCodeEnabled?.let {
        updated = updated.withSetting(LsSettingsKeys.SNYK_CODE_ENABLED, it, changed = true)
      }
      folderConfig.snykIacEnabled?.let {
        updated = updated.withSetting(LsSettingsKeys.SNYK_IAC_ENABLED, it, changed = true)
      }
      folderConfig.issueViewOpenIssues?.let {
        updated = updated.withSetting(LsSettingsKeys.ISSUE_VIEW_OPEN_ISSUES, it, changed = true)
      }
      folderConfig.issueViewIgnoredIssues?.let {
        updated = updated.withSetting(LsSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES, it, changed = true)
      }
      folderConfig.riskScoreThreshold?.let {
        updated = updated.withSetting(LsSettingsKeys.RISK_SCORE_THRESHOLD, it, changed = true)
      }

      fcs.addFolderConfig(updated)
    }
  }

  private fun parseScanCommandConfig(
    scanConfig: Map<String, ScanCommandConfigData>
  ): Map<String, snyk.common.lsp.ScanCommandConfig> {
    val result = mutableMapOf<String, snyk.common.lsp.ScanCommandConfig>()

    for ((product, config) in scanConfig) {
      result[product] =
        snyk.common.lsp.ScanCommandConfig(
          preScanCommand = config.preScanCommand ?: "",
          preScanOnlyReferenceFolder = config.preScanOnlyReferenceFolder ?: false,
          postScanCommand = config.postScanCommand ?: "",
          postScanOnlyReferenceFolder = config.postScanOnlyReferenceFolder ?: false,
        )
    }

    return result
  }
}
