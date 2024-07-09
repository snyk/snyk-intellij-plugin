package io.snyk.plugin.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.getSnykProjectSettingsService
import io.snyk.plugin.isProjectSettingsAvailable
import java.io.File.separator
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
class SnykApplicationSettingsStateService : PersistentStateComponent<SnykApplicationSettingsStateService> {
    val requiredLsProtocolVersion = 12

    var useTokenAuthentication = true
    var currentLSProtocolVersion: Int? = 0
    var isGlobalIgnoresFeatureEnabled = false
    var cliBaseDownloadURL: String = "https://static.snyk.io"
    var cliPath: String = getPluginPath() + separator + Platform.current().snykWrapperFileName
    var cliReleaseChannel = "stable"
    var manageBinariesAutomatically: Boolean = true
    var fileListenerEnabled: Boolean = true
    var token: String? = null
    var customEndpointUrl: String? = null
    var organization: String? = null
    var ignoreUnknownCA = false
    var cliVersion: String? = null
    var scanOnSave: Boolean = true

    // can't be private -> serialization will not work
    @Deprecated("left for old users migration only")
    var cliScanEnable: Boolean = true

    var ossScanEnable: Boolean = true
    var advisorEnable: Boolean = true
    var snykCodeSecurityIssuesScanEnable: Boolean = true
    var snykCodeQualityIssuesScanEnable: Boolean = true
    var iacScanEnabled: Boolean = true
    var containerScanEnabled: Boolean = true

    var sastOnServerEnabled: Boolean? = null
    var sastSettingsError: Boolean? = null
    var localCodeEngineEnabled: Boolean? = null
    var localCodeEngineUrl: String? = ""
    var usageAnalyticsEnabled = true
    var crashReportingEnabled = true

    var lowSeverityEnabled = true
    var mediumSeverityEnabled = true
    var highSeverityEnabled = true
    var criticalSeverityEnabled = true

    var openIssuesEnabled = true
    var ignoredIssuesEnabled = false

    var treeFiltering = TreeFiltering()

    var lastCheckDate: Date? = null
    var pluginFirstRun = true
    var pluginInstalled = false

    // Instant could not be used here due to serialisation Exception
    var pluginFirstInstallTime: Date = Date.from(Instant.now())
    var lastTimeFeedbackRequestShown: Date = Date.from(Instant.now())
    var showFeedbackRequest = true

    var scanningReminderWasShown: Boolean = false

    /**
     * Random UUID used by analytics events if enabled.
     */
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
    }

    fun getAdditionalParameters(project: Project? = null): String? =
        if (isProjectSettingsAvailable(project)) {
            getSnykProjectSettingsService(project!!)?.additionalParameters
        } else {
            ""
        }

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

    fun setSeverityTreeFiltered(
        severity: Severity,
        state: Boolean,
    ) {
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
        ).count { it } == 1

    fun matchFilteringWithEnablement() {
        treeFiltering.criticalSeverity = criticalSeverityEnabled
        treeFiltering.highSeverity = highSeverityEnabled
        treeFiltering.mediumSeverity = mediumSeverityEnabled
        treeFiltering.lowSeverity = lowSeverityEnabled

        treeFiltering.ossResults = ossScanEnable
        treeFiltering.codeSecurityResults = snykCodeSecurityIssuesScanEnable
        treeFiltering.codeQualityResults = snykCodeQualityIssuesScanEnable
        treeFiltering.iacResults = iacScanEnabled
        treeFiltering.containerResults = containerScanEnabled
    }
}

class TreeFiltering {
    var ossResults: Boolean = true
    var codeSecurityResults: Boolean = true
    var codeQualityResults: Boolean = true
    var iacResults: Boolean = true
    var containerResults: Boolean = true

    var lowSeverity = true
    var mediumSeverity = true
    var highSeverity = true
    var criticalSeverity = true
}
