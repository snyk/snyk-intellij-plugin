package io.snyk.plugin.ui.jcef

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.getDefaultCliPath
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.AuthenticationType
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import java.nio.file.Paths
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanCommandConfig
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
  private val executeCommandBridge = ExecuteCommandBridge(project)

  internal fun dispatchSettingsCommand(
    value: String,
    callbackExecutor: ((String, String) -> Unit)? = null,
  ) {
    executeCommandBridge.dispatch(value, callbackExecutor)
  }

  fun generateSaveConfigHandler(
    jbCefBrowser: JBCefBrowserBase,
    nonce: String? = null,
  ): CefLoadHandlerAdapter {
    val saveConfigQuery = JBCefJSQuery.create(jbCefBrowser)
    val saveAttemptFinishedQuery = JBCefJSQuery.create(jbCefBrowser)
    val onFormDirtyChangeQuery = JBCefJSQuery.create(jbCefBrowser)
    val executeCommandQuery = JBCefJSQuery.create(jbCefBrowser)

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

    executeCommandQuery.addHandler { value ->
      dispatchSettingsCommand(value) { callbackId, escaped ->
        invokeLater {
          jbCefBrowser.cefBrowser.executeJavaScript(
            "if(window.__ideCallbacks__&&window.__ideCallbacks__['$callbackId'])" +
              "{window.__ideCallbacks__['$callbackId']($escaped);}",
            jbCefBrowser.cefBrowser.url,
            0,
          )
        }
      }
      JBCefJSQuery.Response("ok")
    }

    return object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if (frame.isMain) {
          val configBridgeScript =
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
                    })();
                    """
          browser.executeJavaScript(configBridgeScript, browser.url, 0)
          browser.executeJavaScript(
            executeCommandBridge.buildBridgeScript(executeCommandQuery),
            browser.url,
            0,
          )
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
      // Detect top-level fields the user explicitly reset to "Project Defaults" (JSON null) and
      // queue a one-shot reset signal to the LS. Gson collapses absent and null into Kotlin null,
      // so this must be done from the raw JSON, not the parsed SaveConfigRequest.
      applyGlobalResetsFromRawJson(jsonString, settings)
      config.folderConfigs?.let { applyFolderConfigs(it) }
    }

    // Notify all open projects' language servers so global settings propagate everywhere.
    // Without this, only the current project's LS/HTML page would reflect global changes.
    for (openProject in ProjectUtil.getOpenProjects()) {
      if (!openProject.isDisposed && !SnykPluginDisposable.getInstance(openProject).isDisposed()) {
        LanguageServerWrapper.getInstance(openProject).updateConfiguration()
      }
    }
  }

  private fun applyGlobalSettings(
    config: SaveConfigRequest,
    settings: SnykApplicationSettingsStateService,
  ) {
    val isFallback = config.isFallbackForm == true

    applyGlobalSetting(
      settings = settings,
      key = LsSettingsKeys.AUTOMATIC_DOWNLOAD,
      isPresent = (config.manageBinariesAutomatically != null),
      newValue = config.manageBinariesAutomatically,
    ) {
      settings.manageBinariesAutomatically = it
    }

    val cliPathProvided = config.cliPath != null
    val resolvedCliPath = config.cliPath?.ifEmpty { getDefaultCliPath() }
    applyGlobalSetting(
      settings = settings,
      key = LsSettingsKeys.CLI_PATH,
      isPresent = cliPathProvided,
      newValue = resolvedCliPath,
    ) {
      settings.cliPath = it
    }

    applyGlobalSetting(
      settings = settings,
      key = LsSettingsKeys.BINARY_BASE_URL,
      isPresent = (config.cliBaseDownloadURL != null),
      newValue = config.cliBaseDownloadURL,
    ) {
      settings.cliBaseDownloadURL = it
    }

    applyGlobalSetting(
      settings = settings,
      key = LsSettingsKeys.CLI_RELEASE_CHANNEL,
      isPresent = (config.cliReleaseChannel != null),
      newValue = config.cliReleaseChannel,
    ) {
      settings.cliReleaseChannel = it
    }

    applyGlobalSetting(
      settings = settings,
      key = LsSettingsKeys.PROXY_INSECURE,
      isPresent = (config.insecure != null),
      newValue = config.insecure,
    ) {
      settings.ignoreUnknownCA = it
    }

    if (!isFallback) {
      // LS collectChangedData sends only diffed fields; each product toggle must be applied only
      // when present (same pattern as severity filters), not coerced from absent → false.
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SNYK_OSS_ENABLED,
        isPresent = (config.activateSnykOpenSource != null),
        newValue = config.activateSnykOpenSource,
      ) {
        settings.ossScanEnable = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SNYK_CODE_ENABLED,
        isPresent = (config.activateSnykCode != null),
        newValue = config.activateSnykCode,
      ) {
        settings.snykCodeSecurityIssuesScanEnable = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SNYK_IAC_ENABLED,
        isPresent = (config.activateSnykIac != null),
        newValue = config.activateSnykIac,
      ) {
        settings.iacScanEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SNYK_SECRETS_ENABLED,
        isPresent = (config.activateSnykSecrets != null),
        newValue = config.activateSnykSecrets,
      ) {
        settings.secretsEnabled = it
      }

      // Scanning mode
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SCAN_AUTOMATIC,
        isPresent = (config.scanningMode != null),
        newValue = config.scanningMode,
      ) {
        settings.scanOnSave = it
      }

      // Connection settings
      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.ORGANIZATION,
        isPresent = (config.organization != null),
        newValue = config.organization,
      ) {
        settings.organization = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.API_ENDPOINT,
        isPresent = (config.endpoint != null),
        newValue = config.endpoint,
      ) {
        settings.customEndpointUrl = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.TOKEN,
        isPresent = (config.token != null),
        newValue = config.token,
      ) {
        settings.token = it
      }

      // Authentication method
      val authMethodProvided = (config.authenticationMethod != null)
      val resolvedAuthMethod =
        config.authenticationMethod?.let { method ->
          when (method) {
            "oauth" -> AuthenticationType.OAUTH2
            "token" -> AuthenticationType.API_TOKEN
            "pat" -> AuthenticationType.PAT
            else -> AuthenticationType.OAUTH2
          }
        }
      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.AUTHENTICATION_METHOD,
        isPresent = authMethodProvided,
        newValue = resolvedAuthMethod,
      ) {
        settings.authenticationType = it
      }

      // Severity filters
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL,
        isPresent = (config.severityFilterCritical != null),
        newValue = config.severityFilterCritical,
      ) {
        settings.criticalSeverityEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SEVERITY_FILTER_HIGH,
        isPresent = (config.severityFilterHigh != null),
        newValue = config.severityFilterHigh,
      ) {
        settings.highSeverityEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM,
        isPresent = (config.severityFilterMedium != null),
        newValue = config.severityFilterMedium,
      ) {
        settings.mediumSeverityEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SEVERITY_FILTER_LOW,
        isPresent = (config.severityFilterLow != null),
        newValue = config.severityFilterLow,
      ) {
        settings.lowSeverityEnabled = it
      }

      // Issue view options (flat booleans from LS HTML; nested issueViewOptions removed)
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES,
        isPresent = (config.issueViewOpenIssues != null),
        newValue = config.issueViewOpenIssues,
      ) {
        settings.openIssuesEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES,
        isPresent = (config.issueViewIgnoredIssues != null),
        newValue = config.issueViewIgnoredIssues,
      ) {
        settings.ignoredIssuesEnabled = it
      }

      // Delta findings
      val hasDeltaFindings = (config.enableDeltaFindings != null)
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SCAN_NET_NEW,
        isPresent = hasDeltaFindings,
        newValue = config.enableDeltaFindings,
      ) {
        settings.setDeltaEnabled(it)
      }

      // Risk score threshold
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.RISK_SCORE_THRESHOLD,
        isPresent = (config.riskScoreThreshold != null),
        newValue = config.riskScoreThreshold,
      ) {
        settings.riskScoreThreshold = it
      }

      // Global (Project Defaults) advanced settings — apply to machine-scope plugin state,
      // not to FolderConfigSettings (those are handled separately in applyFolderConfigs).
      val joinedAdditionalParameters = config.additionalParameters?.let { it.joinToString(" ") }
      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.ADDITIONAL_PARAMETERS,
        isPresent = (config.additionalParameters != null),
        newValue = joinedAdditionalParameters,
      ) {
        settings.globalAdditionalParameters = it ?: ""
      }
      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.ADDITIONAL_ENVIRONMENT,
        isPresent = (config.additionalEnv != null),
        newValue = config.additionalEnv,
      ) {
        settings.globalAdditionalEnvironment = it ?: ""
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
            .map { it.toAbsolutePath().normalize() }
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
            .map { it.toAbsolutePath().normalize() }
            .toSet()

        if (configPaths != currentPaths) {
          settings.markExplicitlyChanged(LsSettingsKeys.TRUSTED_FOLDERS)
        }

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

  /**
   * Org-scope global settings the user can reset to "Project Defaults" from the LS HTML dialog.
   * Each entry maps the JSON field name(s) the HTML/JS may emit to the canonical LS setting key and
   * the action that restores the persisted plugin default so the value is not re-pushed after the
   * one-shot reset signal is consumed.
   *
   * Detection of a reset is by explicit JSON null at the top level (a present field with a non-null
   * value is a normal change, an absent field is "no change"). Gson collapses absent and null into
   * Kotlin null on [SaveConfigRequest], so reset detection must read the raw JSON.
   */
  private fun globalResetSpecs(
    settings: SnykApplicationSettingsStateService
  ): List<Triple<List<String>, String, () -> Unit>> =
    listOf(
      Triple(
        listOf("snyk_oss_enabled", "activateSnykOpenSource"),
        LsFolderSettingsKeys.SNYK_OSS_ENABLED,
      ) {
        settings.ossScanEnable = true
      },
      Triple(
        listOf("snyk_code_enabled", "activateSnykCode"),
        LsFolderSettingsKeys.SNYK_CODE_ENABLED,
      ) {
        settings.snykCodeSecurityIssuesScanEnable = true
      },
      Triple(listOf("snyk_iac_enabled", "activateSnykIac"), LsFolderSettingsKeys.SNYK_IAC_ENABLED) {
        settings.iacScanEnabled = true
      },
      Triple(
        listOf("snyk_secrets_enabled", "activateSnykSecrets"),
        LsFolderSettingsKeys.SNYK_SECRETS_ENABLED,
      ) {
        settings.secretsEnabled = false
      },
      Triple(listOf("scan_automatic"), LsFolderSettingsKeys.SCAN_AUTOMATIC) {
        settings.scanOnSave = true
      },
      Triple(listOf("scan_net_new", "enableDeltaFindings"), LsFolderSettingsKeys.SCAN_NET_NEW) {
        settings.setDeltaEnabled(false)
      },
      Triple(
        listOf("severity_filter_critical", "filterSeverityCritical"),
        LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL,
      ) {
        settings.criticalSeverityEnabled = true
      },
      Triple(
        listOf("severity_filter_high", "filterSeverityHigh"),
        LsFolderSettingsKeys.SEVERITY_FILTER_HIGH,
      ) {
        settings.highSeverityEnabled = true
      },
      Triple(
        listOf("severity_filter_medium", "filterSeverityMedium"),
        LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM,
      ) {
        settings.mediumSeverityEnabled = true
      },
      Triple(
        listOf("severity_filter_low", "filterSeverityLow"),
        LsFolderSettingsKeys.SEVERITY_FILTER_LOW,
      ) {
        settings.lowSeverityEnabled = true
      },
      Triple(listOf("issue_view_open_issues"), LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES) {
        settings.openIssuesEnabled = true
      },
      Triple(listOf("issue_view_ignored_issues"), LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES) {
        settings.ignoredIssuesEnabled = false
      },
      Triple(
        listOf("risk_score_threshold", "riskScoreThreshold"),
        LsFolderSettingsKeys.RISK_SCORE_THRESHOLD,
      ) {
        settings.riskScoreThreshold = null
      },
      Triple(listOf("organization"), LsSettingsKeys.ORGANIZATION) { settings.organization = null },
    )

  /**
   * For each org-scope global key sent as an explicit JSON null at the top level, clear the
   * explicit-change tracking, restore the persisted plugin default, and queue a one-shot `{ value:
   * null, changed: true }` reset signal so the LS Unsets its user:global override exactly once.
   * After the pending reset is consumed by [LanguageServerWrapper.getSettings] the restored default
   * value must NOT re-assert the override on reconnect.
   */
  private fun applyGlobalResetsFromRawJson(
    jsonString: String,
    settings: SnykApplicationSettingsStateService,
  ) {
    val root =
      try {
        JsonParser.parseString(jsonString)
      } catch (e: Exception) {
        logger.warn("Could not parse config JSON for global reset detection", e)
        return
      }
    if (!root.isJsonObject) return
    val obj = root.asJsonObject

    for ((fieldNames, key, restoreDefault) in globalResetSpecs(settings)) {
      val isReset = fieldNames.any { obj.has(it) && obj.get(it).isJsonNull }
      if (isReset) {
        settings.clearExplicitlyChanged(key)
        restoreDefault()
        settings.addPendingReset(key)
      }
    }
  }

  private fun <T> applyGlobalSetting(
    settings: SnykApplicationSettingsStateService,
    key: String,
    isPresent: Boolean,
    newValue: T?,
    assign: (T) -> Unit,
  ) {
    // Diff-based wire contract: the LS settings UI sends only fields that changed. An absent or
    // null field therefore means "no change" and MUST NOT clear the existing explicit-change flag
    // or emit a reset signal. Doing so would silently revoke unrelated user overrides (e.g. the
    // user's cli_path or proxy_insecure) every time any other field is saved, causing the LS to
    // fall back to org/system defaults despite the user's local settings still being set.
    if (!isPresent || newValue == null) {
      return
    }

    // A field PRESENT with a non-null value means the user genuinely asserted it, so ALWAYS mark it
    // explicitly changed. We must NOT auto-clear when the value merely equals the store default:
    // after a "Project Defaults" reset restores a field to its default, re-enabling that field to
    // the same default value is still a real user assertion. Auto-clearing here would make the next
    // getSettings() emit changed:false, so the LS would never learn the user re-asserted it and the
    // user could not set a Project Default equal to the store default (the ADR-1 bug).
    settings.markExplicitlyChanged(key)

    assign(newValue)
  }

  private fun applyFolderSetting(
    existing: snyk.common.lsp.settings.LspFolderConfig,
    settings: SnykApplicationSettingsStateService,
    folderPath: String,
    key: String,
    value: Any,
  ): snyk.common.lsp.settings.LspFolderConfig {
    // Semantics: presence of a field in the JSON payload means the user (or JS form) is
    // asserting a value for it. We propagate it to LS as changed=true regardless of whether
    // the value differs from the previously stored one. Absence of a field is handled by the
    // caller (we simply don't invoke this function).
    settings.markExplicitlyChanged(folderPath, key)
    return existing.withSetting(key, value, changed = true)
  }

  private fun applyFolderConfigs(folderConfigs: List<FolderConfigData>) {
    val fcs = service<FolderConfigSettings>()
    val settings = pluginSettings()

    for (folderConfig in folderConfigs) {
      val folderPath = folderConfig.folderPath
      val existing = fcs.getFolderConfig(folderPath)
      var updated = existing

      folderConfig.additionalParameters?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.ADDITIONAL_PARAMETERS,
            it,
          )
      }

      folderConfig.additionalEnv?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT,
            it,
          )
      }

      folderConfig.scanCommandConfig?.let { scanCommands ->
        val mapped =
          scanCommands.mapValues { (_, v) ->
            ScanCommandConfig(
              preScanCommand = v.preScanCommand ?: "",
              preScanOnlyReferenceFolder = v.preScanOnlyReferenceFolder ?: true,
              postScanCommand = v.postScanCommand ?: "",
              postScanOnlyReferenceFolder = v.postScanOnlyReferenceFolder ?: true,
            )
          }
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SCAN_COMMAND_CONFIG,
            mapped,
          )
      }

      folderConfig.preferredOrg?.let {
        updated =
          applyFolderSetting(updated, settings, folderPath, LsFolderSettingsKeys.PREFERRED_ORG, it)
      }

      folderConfig.autoDeterminedOrg?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.AUTO_DETERMINED_ORG,
            it,
          )
      }
      folderConfig.orgSetByUser?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.ORG_SET_BY_USER,
            it,
          )
      }

      folderConfig.scanAutomatic?.let {
        updated =
          applyFolderSetting(updated, settings, folderPath, LsFolderSettingsKeys.SCAN_AUTOMATIC, it)
      }
      folderConfig.scanNetNew?.let {
        updated =
          applyFolderSetting(updated, settings, folderPath, LsFolderSettingsKeys.SCAN_NET_NEW, it)
      }
      folderConfig.severityFilterCritical?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL,
            it,
          )
      }
      folderConfig.severityFilterHigh?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SEVERITY_FILTER_HIGH,
            it,
          )
      }

      folderConfig.severityFilterMedium?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM,
            it,
          )
      }

      folderConfig.severityFilterLow?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SEVERITY_FILTER_LOW,
            it,
          )
      }

      folderConfig.snykOssEnabled?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SNYK_OSS_ENABLED,
            it,
          )
      }
      folderConfig.snykCodeEnabled?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SNYK_CODE_ENABLED,
            it,
          )
      }
      folderConfig.snykIacEnabled?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SNYK_IAC_ENABLED,
            it,
          )
      }
      folderConfig.snykSecretsEnabled?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.SNYK_SECRETS_ENABLED,
            it,
          )
      }
      folderConfig.issueViewOpenIssues?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES,
            it,
          )
      }
      folderConfig.issueViewIgnoredIssues?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES,
            it,
          )
      }
      folderConfig.riskScoreThreshold?.let {
        updated =
          applyFolderSetting(
            updated,
            settings,
            folderPath,
            LsFolderSettingsKeys.RISK_SCORE_THRESHOLD,
            it,
          )
      }

      fcs.addFolderConfig(updated)
    }
  }
}
