package io.snyk.plugin.services

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(
    name = "SnykProjectSettingsState",
    storages = [Storage("snyk.project.settings.xml", roamingType = RoamingType.DISABLED)]
)
class SnykProjectSettingsStateService : PersistentStateComponent<SnykProjectSettingsStateService> {

    var additionalParameters: String? = null

    override fun getState(): SnykProjectSettingsStateService = this

    override fun loadState(state: SnykProjectSettingsStateService) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
