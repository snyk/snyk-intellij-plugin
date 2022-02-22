package io.snyk.plugin.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import io.snyk.plugin.isProjectSettingsAvailable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.UUID

@Service
@State(
    name = "SnykApplicationSettingsState",
    storages = [Storage("snyk.settings.xml", roamingType = RoamingType.DISABLED)]
)
class SnykApplicationSettingsStateService : PersistentStateComponent<SnykApplicationSettingsStateService> {

    var fileListenerEnabled: Boolean = true
    var token: String? = null
    var customEndpointUrl: String? = null
    var organization: String? = null
    var ignoreUnknownCA = false
    var cliVersion: String? = null

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
    var localCodeEngineEnabled: Boolean? = null
    var usageAnalyticsEnabled = true
    var crashReportingEnabled = true

    var lowSeverityEnabled = true
    var mediumSeverityEnabled = true
    var highSeverityEnabled = true
    var criticalSeverityEnabled = true

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

    fun getAdditionalParameters(project: Project? = null): String? {
        return if (isProjectSettingsAvailable(project)) {
            project!!.service<SnykProjectSettingsStateService>().additionalParameters
        } else {
            ""
        }
    }

    fun getLastCheckDate(): LocalDate? = if (lastCheckDate != null) {
        lastCheckDate!!.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    } else {
        null
    }

    fun setLastCheckDate(localDate: LocalDateTime) {
        this.lastCheckDate = Date.from(localDate.atZone(ZoneId.systemDefault()).toInstant())
    }
}
