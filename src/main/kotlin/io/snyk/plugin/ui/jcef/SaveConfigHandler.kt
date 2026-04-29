package io.snyk.plugin.ui.jcef

import com.google.gson.GsonBuilder
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
      currentValue = { settings.manageBinariesAutomatically },
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
      currentValue = { settings.cliPath },
    ) {
      settings.cliPath = it
    }

    applyGlobalSetting(
      settings = settings,
      key = LsSettingsKeys.BINARY_BASE_URL,
      isPresent = (config.cliBaseDownloadURL != null),
      newValue = config.cliBaseDownloadURL,
      currentValue = { settings.cliBaseDownloadURL },
    ) {
      settings.cliBaseDownloadURL = it
    }

    applyGlobalSetting(
      settings = settings,
      key = LsSettingsKeys.CLI_RELEASE_CHANNEL,
      isPresent = (config.cliReleaseChannel != null),
      newValue = config.cliReleaseChannel,
      currentValue = { settings.cliReleaseChannel },
    ) {
      settings.cliReleaseChannel = it
    }

    applyGlobalSetting(
      settings = settings,
      key = LsSettingsKeys.PROXY_INSECURE,
      isPresent = (config.insecure != null),
      newValue = config.insecure,
      currentValue = { settings.ignoreUnknownCA },
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
        currentValue = { settings.ossScanEnable },
      ) {
        settings.ossScanEnable = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SNYK_CODE_ENABLED,
        isPresent = (config.activateSnykCode != null),
        newValue = config.activateSnykCode,
        currentValue = { settings.snykCodeSecurityIssuesScanEnable },
      ) {
        settings.snykCodeSecurityIssuesScanEnable = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SNYK_IAC_ENABLED,
        isPresent = (config.activateSnykIac != null),
        newValue = config.activateSnykIac,
        currentValue = { settings.iacScanEnabled },
      ) {
        settings.iacScanEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SNYK_SECRETS_ENABLED,
        isPresent = (config.activateSnykSecrets != null),
        newValue = config.activateSnykSecrets,
        currentValue = { settings.secretsEnabled },
      ) {
        settings.secretsEnabled = it
      }

      // Scanning mode
      val hasScanningMode = (config.scanningMode != null)
      val scanOnSaveValue = config.scanningMode?.let { mode -> mode == "auto" }
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SCAN_AUTOMATIC,
        isPresent = hasScanningMode,
        newValue = scanOnSaveValue,
        currentValue = { settings.scanOnSave },
      ) {
        settings.scanOnSave = it
      }

      // Connection settings
      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.ORGANIZATION,
        isPresent = (config.organization != null),
        newValue = config.organization,
        currentValue = { settings.organization },
      ) {
        settings.organization = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.API_ENDPOINT,
        isPresent = (config.endpoint != null),
        newValue = config.endpoint,
        currentValue = { settings.customEndpointUrl },
      ) {
        settings.customEndpointUrl = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsSettingsKeys.TOKEN,
        isPresent = (config.token != null),
        newValue = config.token,
        currentValue = { settings.token },
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
        currentValue = { settings.authenticationType },
      ) {
        settings.authenticationType = it
      }

      // Severity filters
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL,
        isPresent = (config.severityFilterCritical != null),
        newValue = config.severityFilterCritical,
        currentValue = { settings.criticalSeverityEnabled },
      ) {
        settings.criticalSeverityEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SEVERITY_FILTER_HIGH,
        isPresent = (config.severityFilterHigh != null),
        newValue = config.severityFilterHigh,
        currentValue = { settings.highSeverityEnabled },
      ) {
        settings.highSeverityEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM,
        isPresent = (config.severityFilterMedium != null),
        newValue = config.severityFilterMedium,
        currentValue = { settings.mediumSeverityEnabled },
      ) {
        settings.mediumSeverityEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.SEVERITY_FILTER_LOW,
        isPresent = (config.severityFilterLow != null),
        newValue = config.severityFilterLow,
        currentValue = { settings.lowSeverityEnabled },
      ) {
        settings.lowSeverityEnabled = it
      }

      // Issue view options (flat booleans from LS HTML; nested issueViewOptions removed)
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES,
        isPresent = (config.issueViewOpenIssues != null),
        newValue = config.issueViewOpenIssues,
        currentValue = { settings.openIssuesEnabled },
      ) {
        settings.openIssuesEnabled = it
      }

      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES,
        isPresent = (config.issueViewIgnoredIssues != null),
        newValue = config.issueViewIgnoredIssues,
        currentValue = { settings.ignoredIssuesEnabled },
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
        currentValue = { settings.isDeltaFindingsEnabled() },
      ) {
        settings.setDeltaEnabled(it)
      }

      // Risk score threshold
      applyGlobalSetting(
        settings = settings,
        key = LsFolderSettingsKeys.RISK_SCORE_THRESHOLD,
        isPresent = (config.riskScoreThreshold != null),
        newValue = config.riskScoreThreshold,
        currentValue = { settings.riskScoreThreshold },
      ) {
        settings.riskScoreThreshold = it
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

  private fun <T> applyGlobalSetting(
    settings: SnykApplicationSettingsStateService,
    key: String,
    isPresent: Boolean,
    newValue: T?,
    currentValue: () -> Any?,
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

    if (valuesEquivalent(currentValue(), newValue)) {
      settings.clearExplicitlyChanged(key)
    } else {
      settings.markExplicitlyChanged(key)
    }

    assign(newValue)
  }

  private fun valuesEquivalent(current: Any?, next: Any?): Boolean {
    if (current == null || next == null) {
      return current == next
    }

    if (current is Number && next is Number) {
      return current.toDouble() == next.toDouble()
    }

    return current == next
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
