package io.snyk.plugin.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(
    name = "SnykProjectSettingsState",
    storages = [Storage("snyk.project.settings.xml", roamingType = RoamingType.DISABLED)]
)
class SnykProjectSettingsStateService : PersistentStateComponent<SnykProjectSettingsStateService> {

    private var additionalParameters = ""

    override fun getState(): SnykProjectSettingsStateService = this

    override fun loadState(state: SnykProjectSettingsStateService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getAdditionalParameters(): String = additionalParameters

    fun setAdditionalParameters(newParameters: String) {
        this.additionalParameters = newParameters
    }
}
