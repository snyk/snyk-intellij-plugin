package io.snyk.plugin.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.XmlSerializerUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.getDefaultCliPath
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LsFolderSettingsKeys
import snyk.common.lsp.settings.LsSettingsKeys

@Service(Service.Level.APP)
@State(
  name = "SnykApplicationSettingsState",
  storages = [Storage("snyk.settings.xml", roamingType = RoamingType.DISABLED)],
)
class SnykApplicationSettingsStateService :
  PersistentStateComponent<SnykApplicationSettingsStateService> {
  // events
  var pluginInstalledSent: Boolean = false

  val requiredLsProtocolVersion = 25

  @Deprecated("left for old users migration only") var useTokenAuthentication = false
  var authenticationType = AuthenticationType.OAUTH2

  // download settings
  var currentLSProtocolVersion: Int? = 0
  var cliBaseDownloadURL: String = "https://downloads.snyk.io"
  var cliPath: String = getDefaultCliPath()
  var cliReleaseChannel = "stable"
  var manageBinariesAutomatically: Boolean = true

  // testing flag
  var fileListenerEnabled: Boolean = true

  // Concurrent-collection backed (not plain mutableSetOf): APP-scoped, touched from multiple
  // threads
  // (JCEF save thread mutates via mark/clear; the multi-project getSettings() fan-out reads via
  // isExplicitlyChanged; XmlSerializerUtil reflects over this public var off-lock to serialize
  // state). ConcurrentHashMap.newKeySet() iteration is weakly-consistent → every access is CME-safe
  // with no lock. Re-wrapped after loadState (copyBean assigns a plain HashSet) — see [loadState].
  //
  // Tracks only GLOBAL (user:global) keys. Per-folder settings need no equivalent: every IDE writer
  // of a stored folder config sets changed=true on the LspFolderConfig itself
  // ([snyk.common.lsp.settings.withSetting]), which getSettings emits verbatim.
  var explicitChanges: MutableSet<String> = ConcurrentHashMap.newKeySet()

  // Global keys pending a reset signal ({ value: null, changed: true }) to the LS.
  // Transient (not persisted): enqueued by the JCEF query thread, consumed once by every
  // getSettings() across the multi-project fan-out. Concurrent-set backed so all access is
  // lock-free.
  @Transient private val pendingResets: MutableSet<String> = ConcurrentHashMap.newKeySet()

  fun addPendingReset(key: String) {
    pendingResets.add(key)
  }

  // snapshot-then-clear: a key added after toSet() snapshots but before clear() is lost, but a
  // remove-while-iterate drain has the identical weakly-consistent window (a key added past the
  // iterator's bucket is also missed), so it's no safer — both wait for the next consume.
  fun consumePendingResets(): Set<String> {
    val drained = pendingResets.toSet()
    pendingResets.clear()
    return drained
  }

  fun markExplicitlyChanged(settingKey: String) {
    // Cancel any pending reset for this key: the user has set a concrete value, so emitting
    // {value:null, changed:true} on the next getSettings() would discard it. Closes the
    // re-edit-after-reset race — mirrors vscode's ExplicitLspConfigurationChangeTracker.
    pendingResets.remove(settingKey)
    explicitChanges.add(settingKey)
  }

  fun clearExplicitlyChanged(key: String) {
    explicitChanges.remove(key)
  }

  fun clearAllExplicitlyChanged() {
    explicitChanges.clear()
  }

  fun isExplicitlyChanged(settingKey: String): Boolean = explicitChanges.contains(settingKey)

  /**
   * snyk-ls applies user:global settings from InitializationOptions / didChangeConfiguration only
   * when [snyk.common.lsp.settings.ConfigSetting.changed] is true (see snyk-ls `settingBool` and
   * `InitializeSettings` comment). Use persisted [explicitChanges], deviation from plugin defaults,
   * and for [LsSettingsKeys.TRUSTED_FOLDERS] a non-empty path list.
   */
  fun lsUserAssertedChangeForLsConfigurationKey(
    key: String,
    trustedFolderPathsForDeviation: List<String>? = null,
  ): Boolean {
    if (isExplicitlyChanged(key)) return true
    if (key == LsSettingsKeys.TRUSTED_FOLDERS) {
      return trustedFolderPathsForDeviation?.isNotEmpty() == true
    }
    if (machineScopedValueDeviatesFromPluginDefaults(key)) return true
    if (folderScopedGlobalValueDeviatesFromPluginDefaults(key)) return true
    return false
  }

  private fun machineScopedValueDeviatesFromPluginDefaults(key: String): Boolean =
    when (key) {
      LsSettingsKeys.PROXY_INSECURE -> ignoreUnknownCA
      LsSettingsKeys.API_ENDPOINT ->
        normalizedStoredApiEndpointForLs() != PLUGIN_DEFAULT_API_ENDPOINT
      LsSettingsKeys.ORGANIZATION -> !organization.isNullOrBlank()
      LsSettingsKeys.AUTOMATIC_DOWNLOAD -> !manageBinariesAutomatically
      LsSettingsKeys.CLI_PATH -> cliPath != getDefaultCliPath()
      LsSettingsKeys.BINARY_BASE_URL ->
        (cliBaseDownloadURL ?: "").trim() != PLUGIN_DEFAULT_BINARY_BASE_URL
      LsSettingsKeys.CLI_RELEASE_CHANNEL -> cliReleaseChannel != PLUGIN_DEFAULT_CLI_RELEASE_CHANNEL
      LsSettingsKeys.AUTHENTICATION_METHOD -> authenticationType != AuthenticationType.OAUTH2
      LsSettingsKeys.ADDITIONAL_PARAMETERS -> globalAdditionalParameters.isNotBlank()
      LsSettingsKeys.ADDITIONAL_ENVIRONMENT -> globalAdditionalEnvironment.isNotBlank()
      else -> false
    }

  /** Same normalization as [snyk.common.resolveCustomEndpoint] without mutating state. */
  private fun normalizedStoredApiEndpointForLs(): String {
    val raw = customEndpointUrl?.trim().orEmpty()
    if (raw.isEmpty()) return PLUGIN_DEFAULT_API_ENDPOINT
    return raw
      .removeSuffix("/")
      .removeSuffix("/api")
      .replace("https://snyk.io", "https://api.snyk.io")
      .replace("https://app.", "https://api.")
  }

  private fun folderScopedGlobalValueDeviatesFromPluginDefaults(key: String): Boolean =
    when (key) {
      LsFolderSettingsKeys.SNYK_OSS_ENABLED -> ossScanEnable != PLUGIN_DEFAULT_OSS_SCAN_ENABLE
      LsFolderSettingsKeys.SNYK_CODE_ENABLED ->
        snykCodeSecurityIssuesScanEnable != PLUGIN_DEFAULT_CODE_SCAN_ENABLE
      LsFolderSettingsKeys.SNYK_IAC_ENABLED -> iacScanEnabled != PLUGIN_DEFAULT_IAC_SCAN_ENABLE
      LsFolderSettingsKeys.SNYK_SECRETS_ENABLED ->
        secretsEnabled != PLUGIN_DEFAULT_SECRETS_SCAN_ENABLE
      LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL ->
        criticalSeverityEnabled != PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED
      LsFolderSettingsKeys.SEVERITY_FILTER_HIGH ->
        highSeverityEnabled != PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED
      LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM ->
        mediumSeverityEnabled != PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED
      LsFolderSettingsKeys.SEVERITY_FILTER_LOW ->
        lowSeverityEnabled != PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED
      LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES ->
        openIssuesEnabled != PLUGIN_DEFAULT_OPEN_ISSUES_ENABLED
      LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES ->
        ignoredIssuesEnabled != PLUGIN_DEFAULT_IGNORED_ISSUES_ENABLED
      LsFolderSettingsKeys.SCAN_AUTOMATIC -> scanOnSave != PLUGIN_DEFAULT_SCAN_ON_SAVE
      LsFolderSettingsKeys.SCAN_NET_NEW ->
        isDeltaFindingsEnabled() != PLUGIN_DEFAULT_DELTA_FINDINGS_ENABLED
      LsFolderSettingsKeys.RISK_SCORE_THRESHOLD ->
        riskScoreThreshold != PLUGIN_DEFAULT_RISK_SCORE_THRESHOLD
      else -> false
    }

  /**
   * Single source of truth for per-key "Project Defaults" values. Restores the global setting
   * behind [key] to the SAME plugin default the deviation checks
   * ([folderScopedGlobalValueDeviatesFromPluginDefaults] /
   * [machineScopedValueDeviatesFromPluginDefaults]) recognize, so a reset can never drift from what
   * the deviation check considers "at default". Callers own the explicit-change / pending-reset
   * bookkeeping (see [io.snyk.plugin.ui.jcef.SaveConfigHandler]); this only writes the field.
   */
  fun resetGlobalKeyToDefault(key: String) {
    when (key) {
      LsFolderSettingsKeys.SNYK_OSS_ENABLED -> ossScanEnable = PLUGIN_DEFAULT_OSS_SCAN_ENABLE
      LsFolderSettingsKeys.SNYK_CODE_ENABLED ->
        snykCodeSecurityIssuesScanEnable = PLUGIN_DEFAULT_CODE_SCAN_ENABLE
      LsFolderSettingsKeys.SNYK_IAC_ENABLED -> iacScanEnabled = PLUGIN_DEFAULT_IAC_SCAN_ENABLE
      LsFolderSettingsKeys.SNYK_SECRETS_ENABLED ->
        secretsEnabled = PLUGIN_DEFAULT_SECRETS_SCAN_ENABLE
      LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL ->
        criticalSeverityEnabled = PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED
      LsFolderSettingsKeys.SEVERITY_FILTER_HIGH ->
        highSeverityEnabled = PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED
      LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM ->
        mediumSeverityEnabled = PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED
      LsFolderSettingsKeys.SEVERITY_FILTER_LOW ->
        lowSeverityEnabled = PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED
      LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES ->
        openIssuesEnabled = PLUGIN_DEFAULT_OPEN_ISSUES_ENABLED
      LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES ->
        ignoredIssuesEnabled = PLUGIN_DEFAULT_IGNORED_ISSUES_ENABLED
      LsFolderSettingsKeys.SCAN_AUTOMATIC -> scanOnSave = PLUGIN_DEFAULT_SCAN_ON_SAVE
      LsFolderSettingsKeys.SCAN_NET_NEW -> setDeltaEnabled(PLUGIN_DEFAULT_DELTA_FINDINGS_ENABLED)
      LsFolderSettingsKeys.RISK_SCORE_THRESHOLD ->
        riskScoreThreshold = PLUGIN_DEFAULT_RISK_SCORE_THRESHOLD
      LsSettingsKeys.ORGANIZATION -> organization = PLUGIN_DEFAULT_ORGANIZATION
      else -> error("No plugin default known for reset key: $key")
    }
  }

  /**
   * Call when the user saves scan types (classic settings) or product toggles from the LS HTML
   * dialog so the full product vector is sent to the LS with `changed: true` (needed when some
   * toggles match plugin defaults but must still override remote/LDX state).
   */
  fun markAllProductEnablementKeysExplicit() {
    markExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED)
    markExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED)
    markExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    markExplicitlyChanged(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED)
  }

  private fun reconcileProductExplicitKeysIfDeviatingFromDefaults() {
    if (
      ossScanEnable != PLUGIN_DEFAULT_OSS_SCAN_ENABLE ||
        snykCodeSecurityIssuesScanEnable != PLUGIN_DEFAULT_CODE_SCAN_ENABLE ||
        iacScanEnabled != PLUGIN_DEFAULT_IAC_SCAN_ENABLE ||
        secretsEnabled != PLUGIN_DEFAULT_SECRETS_SCAN_ENABLE
    ) {
      markAllProductEnablementKeysExplicit()
    }
  }

  // TODO migrate to
  // https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html?from=jetbrains.org
  var token: String? = null
  var customEndpointUrl: String? = null
  var organization: String? = null
  var ignoreUnknownCA = false
  var cliVersion: String? = null
  var cliSha256: String? = null
  var scanOnSave: Boolean = true

  // can't be private -> serialization will not work
  @Deprecated("left for old users migration only") var cliScanEnable: Boolean = true

  // products enablement store
  var ossScanEnable: Boolean = true
  var snykCodeSecurityIssuesScanEnable: Boolean = true
  var iacScanEnabled: Boolean = true
  var secretsEnabled: Boolean = false

  // feature flag / server-side enablement
  var sastOnServerEnabled: Boolean? = null
  var sastSettingsError: Boolean? = null

  // filters enablement store
  var issuesToDisplay: String = DISPLAY_ALL_ISSUES // delta
  var lowSeverityEnabled = true
  var mediumSeverityEnabled = true
  var highSeverityEnabled = true
  var criticalSeverityEnabled = true
  var riskScoreThreshold: Int? = null
  var treeFiltering = TreeFiltering()

  // ignore display option store
  var isGlobalIgnoresFeatureEnabled = false
  var openIssuesEnabled = true
  var ignoredIssuesEnabled = false

  var lastCheckDate: Date? = null
  var pluginFirstRun = true

  // Instant could not be used here due to serialisation Exception
  var lastTimeFeedbackRequestShown: Date = Date.from(Instant.now())
  var showFeedbackRequest = true

  // Global (Project Defaults) advanced settings — sent as top-level entries in the LS settings map.
  // Distinct from per-folder additional_parameters / additional_environment in
  // FolderConfigSettings.
  var globalAdditionalParameters: String = ""
  var globalAdditionalEnvironment: String = ""

  /** Random UUID used by analytics events if enabled. */
  var userAnonymousId = UUID.randomUUID().toString()

  override fun getState(): SnykApplicationSettingsStateService = this

  override fun loadState(state: SnykApplicationSettingsStateService) {
    XmlSerializerUtil.copyBean(state, this)
    // copyBean reflects the deserialized value in as a plain HashSet, dropping the concurrent
    // backing. Re-wrap so off-lock serializer/reader iteration stays CME-safe after every IDE
    // start.
    explicitChanges = ConcurrentHashMap.newKeySet<String>().apply { addAll(explicitChanges) }
  }

  @Suppress("DEPRECATION")
  override fun initializeComponent() {
    super.initializeComponent()
    // migration for old users
    if (!cliScanEnable) {
      ossScanEnable = false
      cliScanEnable = true // drop prev state
    }

    // Migration from old settings. OAuth2 is default, so we only need to handle migration for API
    // Token users.
    if (useTokenAuthentication) {
      authenticationType = AuthenticationType.API_TOKEN
      useTokenAuthentication = false
    }

    reconcileProductExplicitKeysIfDeviatingFromDefaults()
  }

  fun isDeltaFindingsEnabled(): Boolean = (issuesToDisplay == DISPLAY_NEW_ISSUES)

  fun getLastCheckDate(): LocalDate? =
    if (lastCheckDate != null) {
      lastCheckDate!!.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    } else {
      null
    }

  fun setLastCheckDate(localDate: LocalDateTime) {
    this.lastCheckDate = Date.from(localDate.atZone(ZoneId.systemDefault()).toInstant())
  }

  fun hasSeverityEnabled(severity: Severity): Boolean =
    when (severity) {
      Severity.CRITICAL -> criticalSeverityEnabled
      Severity.HIGH -> highSeverityEnabled
      Severity.MEDIUM -> mediumSeverityEnabled
      Severity.LOW -> lowSeverityEnabled
      else -> false
    }

  fun hasSeverityEnabledAndFiltered(severity: Severity): Boolean = hasSeverityEnabled(severity)

  fun hasSeverityEnabledForFile(severity: Severity, file: VirtualFile, project: Project): Boolean {
    val fcs = service<FolderConfigSettings>()
    return fcs.getSeverityFilterForFile(severity, file, project) ?: hasSeverityEnabled(severity)
  }

  fun hasSeverityEnabledAndFilteredForFile(
    severity: Severity,
    file: VirtualFile,
    project: Project,
  ): Boolean = hasSeverityEnabledForFile(severity, file, project)

  fun hasOnlyOneSeverityEnabled(): Boolean =
    arrayOf(
        hasSeverityEnabledAndFiltered(Severity.CRITICAL),
        hasSeverityEnabledAndFiltered(Severity.HIGH),
        hasSeverityEnabledAndFiltered(Severity.MEDIUM),
        hasSeverityEnabledAndFiltered(Severity.LOW),
      )
      .count { it } == 1

  /**
   * Like [hasOnlyOneSeverityEnabled] but uses
   * [FolderConfigSettings.isSeverityEnabledForProjectToolWindow] so the toolbar guard matches
   * multi-root folder severity when [getFolderConfigs] is non-empty.
   */
  fun hasOnlyOneSeverityTreeFilterActive(project: Project): Boolean {
    val fcs = service<FolderConfigSettings>()
    return listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW).count { sev ->
      fcs.isSeverityEnabledForProjectToolWindow(sev, project, hasSeverityEnabled(sev))
    } == 1
  }

  /**
   * Default the per-product tree filter to match the product's enablement so that re-enabling a
   * product in Settings makes its branch visible again without a separate toolbar click. Severity
   * filtering is folder-scoped (see [FolderConfigSettings.setSeverityEnabledForProject]) and is not
   * handled here.
   */
  fun matchFilteringWithEnablement() {
    treeFiltering.ossResults = ossScanEnable
    treeFiltering.codeSecurityResults = snykCodeSecurityIssuesScanEnable
    treeFiltering.iacResults = iacScanEnabled
  }

  fun setDeltaEnabled(enabled: Boolean) {
    issuesToDisplay =
      if (enabled) {
        DISPLAY_NEW_ISSUES
      } else {
        DISPLAY_ALL_ISSUES
      }
  }

  companion object {
    const val DISPLAY_ALL_ISSUES = "All issues"
    const val DISPLAY_NEW_ISSUES = "Net new issues"

    private const val PLUGIN_DEFAULT_OSS_SCAN_ENABLE = true
    private const val PLUGIN_DEFAULT_CODE_SCAN_ENABLE = true
    private const val PLUGIN_DEFAULT_IAC_SCAN_ENABLE = true
    private const val PLUGIN_DEFAULT_SECRETS_SCAN_ENABLE = false
    private const val PLUGIN_DEFAULT_OPEN_ISSUES_ENABLED = true
    private const val PLUGIN_DEFAULT_IGNORED_ISSUES_ENABLED = false
    private const val PLUGIN_DEFAULT_SCAN_ON_SAVE = true
    private const val PLUGIN_DEFAULT_DELTA_FINDINGS_ENABLED = false
    private const val PLUGIN_DEFAULT_SEVERITY_FILTER_ENABLED = true
    private val PLUGIN_DEFAULT_RISK_SCORE_THRESHOLD: Int? = null
    private val PLUGIN_DEFAULT_ORGANIZATION: String? = null

    private const val PLUGIN_DEFAULT_API_ENDPOINT = "https://api.snyk.io"
    private const val PLUGIN_DEFAULT_BINARY_BASE_URL = "https://downloads.snyk.io"
    private const val PLUGIN_DEFAULT_CLI_RELEASE_CHANNEL = "stable"
  }
}

enum class AuthenticationType(
  val languageServerSettingsName: String,
  val dialogName: String,
  val dialogIndex: Int,
) {
  OAUTH2("oauth", "OAuth2 (Recommended)", 0),
  PAT("pat", "Personal Access Token", 1),
  API_TOKEN("token", "API Token (Legacy)", 2),
}

class TreeFiltering {
  var ossResults: Boolean = true
  var codeSecurityResults: Boolean = true
  var iacResults: Boolean = true
}
