package io.snyk.plugin.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import io.snyk.plugin.isProjectSettingsAvailable
import java.time.LocalDate

@Service
@State(
    name = "SnykApplicationSettingsState",
    storages = [Storage("snyk.settings.xml", roamingType = RoamingType.DISABLED)]
)
class SnykApplicationSettingsStateService : PersistentStateComponent<SnykApplicationSettingsStateService> {

    private var customEndpointUrl = ""
    private var organization = ""
    private var ignoreUnknownCA = false
    private var cliVersion = ""
    private var lastCheckDate: LocalDate? = null

    override fun getState(): SnykApplicationSettingsStateService = this

    override fun loadState(state: SnykApplicationSettingsStateService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getCustomEndpointUrl(): String = customEndpointUrl

    fun setCustomEndpointUrl(newCustomEndpoint: String) {
        this.customEndpointUrl = newCustomEndpoint
    }

    fun getOrganization(): String = organization

    fun setOrganization(newOrganization: String) {
        this.organization = newOrganization
    }

    fun isIgnoreUnknownCA(): Boolean = ignoreUnknownCA

    fun setIgnoreUnknownCA(newIgnoreUnknownCA: Boolean) {
        this.ignoreUnknownCA = newIgnoreUnknownCA
    }

    fun getCliVersion(): String = cliVersion

    fun setCliVersion(newCliVersion: String) {
        this.cliVersion = newCliVersion
    }

    fun getLastCheckDate(): LocalDate? = lastCheckDate

    fun setLastCheckDate(newDate: LocalDate?) {
        lastCheckDate = newDate
    }

    fun getAdditionalParameters(project: Project? = null): String {
        return if (isProjectSettingsAvailable(project)) {
            project!!.service<SnykProjectSettingsStateService>().getAdditionalParameters()
        } else {
            ""
        }
    }
}
