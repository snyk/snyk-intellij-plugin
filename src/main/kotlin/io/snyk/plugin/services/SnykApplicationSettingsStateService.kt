package io.snyk.plugin.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import io.snyk.plugin.isProjectSettingsAvailable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Service
@State(
    name = "SnykApplicationSettingsState",
    storages = [Storage("snyk.settings.xml", roamingType = RoamingType.DISABLED)]
)
class SnykApplicationSettingsStateService : PersistentStateComponent<SnykApplicationSettingsStateService> {

    var token: String? = null
    var customEndpointUrl: String? = null
    var organization: String? = null
    var ignoreUnknownCA = false
    var cliVersion: String? = null

    var cliScanEnable: Boolean = true
    var snykCodeSecurityIssuesScanEnable: Boolean = true
    var snykCodeQualityIssuesScanEnable: Boolean = false
    var sastOnServerEnabled = false
    var usageAnalyticsEnabled = true

    var lowSeverityEnabled = true
    var mediumSeverityEnabled = true
    var highSeverityEnabled = true
    var criticalSeverityEnabled = true

    var lastCheckDate: Date? = null
    var pluginFirstRun = true
    // Instant could not be used here due to serialisation Exception
    var pluginFirstInstallTime: Date = Date.from(Instant.now())
    var lastTimeFeedbackRequestShown: Date = Date.from(Instant.now()) // we'll give 2 weeks to evaluate initially
    var showFeedbackRequest = true

    override fun getState(): SnykApplicationSettingsStateService = this

    override fun loadState(state: SnykApplicationSettingsStateService) {
        XmlSerializerUtil.copyBean(state, this)
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
        this.lastCheckDate = Date.from(localDate.atZone(ZoneId.systemDefault()).toInstant());
    }
}
