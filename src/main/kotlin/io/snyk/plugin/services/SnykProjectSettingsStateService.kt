package io.snyk.plugin.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service
@State(
  name = "SnykProjectSettingsState",
  storages = [Storage("snyk.project.settings.xml", roamingType = RoamingType.DISABLED)],
)
class SnykProjectSettingsStateService : PersistentStateComponent<SnykProjectSettingsStateService> {

  override fun getState(): SnykProjectSettingsStateService = this

  override fun loadState(state: SnykProjectSettingsStateService) {
    XmlSerializerUtil.copyBean(state, this)
  }
}
