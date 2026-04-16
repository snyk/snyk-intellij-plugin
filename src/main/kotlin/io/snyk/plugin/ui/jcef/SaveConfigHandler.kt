package io.snyk.plugin.ui.jcef

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
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

    LanguageServerWrapper.getInstance(project).updateConfiguration()
  }

  private fun applyGlobalSettings(
    config: SaveConfigRequest,
    settings: SnykApplicationSettingsStateService,
  ) {
    val isFallback = config.isFallbackForm == true

    config.manageBinariesAutomatically?.let {
      if (it != settings.manageBinariesAutomatically) {
        settings.markExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
      }
      settings.manageBinariesAutomatically = it
    }
      ?: run {
        settings.clearExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
        settings.addPendingReset(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
      }

    // Use the provided cliPath from the config if present, or the default CLI path if not.
    config.cliPath?.let { path ->
      val resolved = path.ifEmpty { getDefaultCliPath() }
      if (resolved != settings.cliPath) {
        settings.markExplicitlyChanged(LsSettingsKeys.CLI_PATH)
      }
      settings.cliPath = resolved
    }
      ?: run {
        settings.clearExplicitlyChanged(LsSettingsKeys.CLI_PATH)
        settings.addPendingReset(LsSettingsKeys.CLI_PATH)
      }

    config.cliBaseDownloadURL?.let {
      if (it != settings.cliBaseDownloadURL) {
        settings.markExplicitlyChanged(LsSettingsKeys.BINARY_BASE_URL)
      }
      settings.cliBaseDownloadURL = it
    }
      ?: run {
        settings.clearExplicitlyChanged(LsSettingsKeys.BINARY_BASE_URL)
        settings.addPendingReset(LsSettingsKeys.BINARY_BASE_URL)
      }
    config.cliReleaseChannel?.let {
      if (it != settings.cliReleaseChannel) {
        settings.markExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL)
      }
      settings.cliReleaseChannel = it
    }
      ?: run {
        settings.clearExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL)
        settings.addPendingReset(LsSettingsKeys.CLI_RELEASE_CHANNEL)
      }
    config.insecure?.let {
      if (it != settings.ignoreUnknownCA) {
        settings.markExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE)
      }
      settings.ignoreUnknownCA = it
    }
      ?: run {
        settings.clearExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE)
        settings.addPendingReset(LsSettingsKeys.PROXY_INSECURE)
      }

    if (!isFallback) {
      val newOss = config.activateSnykOpenSource ?: false
      if (newOss != settings.ossScanEnable) {
        settings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED)
      }
      settings.ossScanEnable = newOss
      val newCode = config.activateSnykCode ?: false
      if (newCode != settings.snykCodeSecurityIssuesScanEnable) {
        settings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED)
      }
      settings.snykCodeSecurityIssuesScanEnable = newCode
      val newIac = config.activateSnykIac ?: false
      if (newIac != settings.iacScanEnabled) {
        settings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
      }
      settings.iacScanEnabled = newIac
      val newSecrets = config.activateSnykSecrets ?: false
      if (newSecrets != settings.secretsEnabled) {
        settings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED)
      }
      settings.secretsEnabled = newSecrets

      // Scanning mode
      config.scanningMode?.let { mode ->
        val newScanOnSave = (mode == "auto")
        if (newScanOnSave != settings.scanOnSave) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.SCAN_AUTOMATIC)
        }
        settings.scanOnSave = newScanOnSave
      }

      // Connection settings
      config.organization?.let {
        if (it != settings.organization) {
          settings.markExplicitlyChanged(LsSettingsKeys.ORGANIZATION)
        }
        settings.organization = it
      }
        ?: run {
          settings.clearExplicitlyChanged(LsSettingsKeys.ORGANIZATION)
          settings.addPendingReset(LsSettingsKeys.ORGANIZATION)
        }
      config.endpoint?.let {
        if (it != settings.customEndpointUrl) {
          settings.markExplicitlyChanged(LsSettingsKeys.API_ENDPOINT)
        }
        settings.customEndpointUrl = it
      }
        ?: run {
          settings.clearExplicitlyChanged(LsSettingsKeys.API_ENDPOINT)
          settings.addPendingReset(LsSettingsKeys.API_ENDPOINT)
        }
      config.token?.let {
        if (it != settings.token) {
          settings.markExplicitlyChanged(LsSettingsKeys.TOKEN)
        }
        settings.token = it
      }
        ?: run {
          settings.clearExplicitlyChanged(LsSettingsKeys.TOKEN)
          settings.addPendingReset(LsSettingsKeys.TOKEN)
        }

      // Authentication method
      config.authenticationMethod?.let { method ->
        val resolved =
          when (method) {
            "oauth" -> AuthenticationType.OAUTH2
            "token" -> AuthenticationType.API_TOKEN
            "pat" -> AuthenticationType.PAT
            else -> AuthenticationType.OAUTH2
          }
        if (resolved != settings.authenticationType) {
          settings.markExplicitlyChanged(LsSettingsKeys.AUTHENTICATION_METHOD)
        }
        settings.authenticationType = resolved
      }
        ?: run {
          settings.clearExplicitlyChanged(LsSettingsKeys.AUTHENTICATION_METHOD)
          settings.addPendingReset(LsSettingsKeys.AUTHENTICATION_METHOD)
        }

      // Severity filters
      config.severityFilterCritical?.let {
        if (it != settings.criticalSeverityEnabled) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)
        }
        settings.criticalSeverityEnabled = it
      }
      config.severityFilterHigh?.let {
        if (it != settings.highSeverityEnabled) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH)
        }
        settings.highSeverityEnabled = it
      }
      config.severityFilterMedium?.let {
        if (it != settings.mediumSeverityEnabled) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM)
        }
        settings.mediumSeverityEnabled = it
      }
      config.severityFilterLow?.let {
        if (it != settings.lowSeverityEnabled) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_LOW)
        }
        settings.lowSeverityEnabled = it
      }

      // Issue view options (flat booleans from LS HTML; nested issueViewOptions removed)
      config.issueViewOpenIssues?.let {
        if (it != settings.openIssuesEnabled) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES)
        }
        settings.openIssuesEnabled = it
      }
      config.issueViewIgnoredIssues?.let {
        if (it != settings.ignoredIssuesEnabled) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES)
        }
        settings.ignoredIssuesEnabled = it
      }

      // Delta findings
      config.enableDeltaFindings?.let {
        if (it != settings.isDeltaFindingsEnabled()) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.SCAN_NET_NEW)
        }
        settings.setDeltaEnabled(it)
      }

      // Risk score threshold
      config.riskScoreThreshold?.let {
        if (it != settings.riskScoreThreshold) {
          settings.markExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)
        }
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

  private fun applyFolderConfigs(folderConfigs: List<FolderConfigData>) {
    val fcs = service<FolderConfigSettings>()

    for (folderConfig in folderConfigs) {
      val existing = fcs.getFolderConfig(folderConfig.folderPath)
      var updated = existing

      folderConfig.additionalParameters?.let { newVal ->
        val oldVal =
          (existing.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS, newVal, changed = changed)
      }
      folderConfig.additionalEnv?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT)?.value as? String
        val changed = oldVal != newVal
        updated =
          updated.withSetting(
            LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT,
            newVal,
            changed = changed,
          )
      }
      folderConfig.preferredOrg?.let { newVal ->
        val oldVal = existing.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value as? String
        val changed = oldVal != newVal
        updated = updated.withSetting(LsFolderSettingsKeys.PREFERRED_ORG, newVal, changed = changed)
      }
      folderConfig.autoDeterminedOrg?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.AUTO_DETERMINED_ORG)?.value as? String
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.AUTO_DETERMINED_ORG, newVal, changed = changed)
      }
      folderConfig.orgSetByUser?.let { newVal ->
        val oldVal = existing.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.ORG_SET_BY_USER, newVal, changed = changed)
      }
      folderConfig.scanCommandConfig?.let { newVal ->
        val parsed = parseScanCommandConfig(newVal)
        val oldVal = existing.settings?.get(LsFolderSettingsKeys.SCAN_COMMAND_CONFIG)?.value
        val changed = oldVal != parsed
        updated =
          updated.withSetting(LsFolderSettingsKeys.SCAN_COMMAND_CONFIG, parsed, changed = changed)
      }

      folderConfig.scanAutomatic?.let { newVal ->
        val oldVal = existing.settings?.get(LsFolderSettingsKeys.SCAN_AUTOMATIC)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.SCAN_AUTOMATIC, newVal, changed = changed)
      }
      folderConfig.scanNetNew?.let { newVal ->
        val oldVal = existing.settings?.get(LsFolderSettingsKeys.SCAN_NET_NEW)?.value as? Boolean
        val changed = oldVal != newVal
        updated = updated.withSetting(LsFolderSettingsKeys.SCAN_NET_NEW, newVal, changed = changed)
      }
      folderConfig.severityFilterCritical?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(
            LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL,
            newVal,
            changed = changed,
          )
      }
      folderConfig.severityFilterHigh?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH, newVal, changed = changed)
      }
      folderConfig.severityFilterMedium?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(
            LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM,
            newVal,
            changed = changed,
          )
      }
      folderConfig.severityFilterLow?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_LOW)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.SEVERITY_FILTER_LOW, newVal, changed = changed)
      }
      folderConfig.snykOssEnabled?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.SNYK_OSS_ENABLED)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.SNYK_OSS_ENABLED, newVal, changed = changed)
      }
      folderConfig.snykCodeEnabled?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.SNYK_CODE_ENABLED, newVal, changed = changed)
      }
      folderConfig.snykIacEnabled?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.SNYK_IAC_ENABLED)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.SNYK_IAC_ENABLED, newVal, changed = changed)
      }
      folderConfig.snykSecretsEnabled?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED, newVal, changed = changed)
      }
      folderConfig.issueViewOpenIssues?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(
            LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES,
            newVal,
            changed = changed,
          )
      }
      folderConfig.issueViewIgnoredIssues?.let { newVal ->
        val oldVal =
          existing.settings?.get(LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES)?.value as? Boolean
        val changed = oldVal != newVal
        updated =
          updated.withSetting(
            LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES,
            newVal,
            changed = changed,
          )
      }
      folderConfig.riskScoreThreshold?.let { newVal ->
        val oldRaw = existing.settings?.get(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)?.value
        val oldVal = coerceFolderSettingInt(oldRaw)
        val newInt = coerceFolderSettingInt(newVal) ?: return@let
        val changed = oldVal != newInt
        updated =
          updated.withSetting(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD, newInt, changed = changed)
      }

      fcs.addFolderConfig(updated)
    }
  }

  /**
   * Folder setting maps may hold [java.lang.Number] subtypes (e.g. Gson doubles). Normalize to
   * [Int] so comparisons and persisted values stay consistent for language-server settings
   * payloads.
   */
  private fun coerceFolderSettingInt(raw: Any?): Int? =
    when (raw) {
      null -> null
      is java.lang.Number -> raw.intValue()
      else -> null
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
