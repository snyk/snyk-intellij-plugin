package io.snyk.plugin.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.getDefaultCliPath
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID

@Service
@State(
  name = "SnykApplicationSettingsState",
  storages = [Storage("snyk.settings.xml", roamingType = RoamingType.DISABLED)],
)
class SnykApplicationSettingsStateService :
  PersistentStateComponent<SnykApplicationSettingsStateService> {
  // events
  var pluginInstalledSent: Boolean = false

  val requiredLsProtocolVersion = 24

  @Deprecated("left for old users migration only") var useTokenAuthentication = false
  var authenticationType = AuthenticationType.OAUTH2
  var currentLSProtocolVersion: Int? = 0
  var isGlobalIgnoresFeatureEnabled = false
  var cliBaseDownloadURL: String = "https://downloads.snyk.io"
  var cliPath: String = getDefaultCliPath()
  var cliReleaseChannel = "stable"
  var issuesToDisplay: String = DISPLAY_ALL_ISSUES
  var manageBinariesAutomatically: Boolean = true
  var fileListenerEnabled: Boolean = true
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

  var ossScanEnable: Boolean = true
  var snykCodeSecurityIssuesScanEnable: Boolean = true
  var iacScanEnabled: Boolean = true
  var sastOnServerEnabled: Boolean? = null
  var sastSettingsError: Boolean? = null
  var lowSeverityEnabled = true
  var mediumSeverityEnabled = true
  var highSeverityEnabled = true
  var criticalSeverityEnabled = true

  var openIssuesEnabled = true
  var ignoredIssuesEnabled = false

  var riskScoreThreshold: Int? = null

  var treeFiltering = TreeFiltering()

  var lastCheckDate: Date? = null
  var pluginFirstRun = true

  // Instant could not be used here due to serialisation Exception
  var lastTimeFeedbackRequestShown: Date = Date.from(Instant.now())
  var showFeedbackRequest = true

  /** Random UUID used by analytics events if enabled. */
  var userAnonymousId = UUID.randomUUID().toString()

  override fun getState(): SnykApplicationSettingsStateService = this

  override fun loadState(state: SnykApplicationSettingsStateService) {
    XmlSerializerUtil.copyBean(state, this)
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

  fun hasSeverityTreeFiltered(severity: Severity): Boolean =
    when (severity) {
      Severity.CRITICAL -> treeFiltering.criticalSeverity
      Severity.HIGH -> treeFiltering.highSeverity
      Severity.MEDIUM -> treeFiltering.mediumSeverity
      Severity.LOW -> treeFiltering.lowSeverity
      else -> false
    }

  fun setSeverityTreeFiltered(severity: Severity, state: Boolean) {
    when (severity) {
      Severity.CRITICAL -> treeFiltering.criticalSeverity = state
      Severity.HIGH -> treeFiltering.highSeverity = state
      Severity.MEDIUM -> treeFiltering.mediumSeverity = state
      Severity.LOW -> treeFiltering.lowSeverity = state
      else -> throw IllegalArgumentException("Unknown severity: $severity")
    }
  }

  fun hasSeverityEnabledAndFiltered(severity: Severity): Boolean =
    hasSeverityEnabled(severity) && hasSeverityTreeFiltered(severity)

  fun hasOnlyOneSeverityEnabled(): Boolean =
    arrayOf(
        hasSeverityEnabledAndFiltered(Severity.CRITICAL),
        hasSeverityEnabledAndFiltered(Severity.HIGH),
        hasSeverityEnabledAndFiltered(Severity.MEDIUM),
        hasSeverityEnabledAndFiltered(Severity.LOW),
      )
      .count { it } == 1

  fun matchFilteringWithEnablement() {
    treeFiltering.criticalSeverity = criticalSeverityEnabled
    treeFiltering.highSeverity = highSeverityEnabled
    treeFiltering.mediumSeverity = mediumSeverityEnabled
    treeFiltering.lowSeverity = lowSeverityEnabled

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

  var lowSeverity = true
  var mediumSeverity = true
  var highSeverity = true
  var criticalSeverity = true
}
