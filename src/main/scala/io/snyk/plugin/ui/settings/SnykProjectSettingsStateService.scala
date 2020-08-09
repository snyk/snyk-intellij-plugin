package io.snyk.plugin.ui.settings

import java.util.Objects.isNull

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
  name = "SnykProjectSettingsState",
  storages = Array(new Storage("snyk.project.settings.xml"))
)
class SnykProjectSettingsStateService
  extends PersistentStateComponent[SnykProjectSettingsStateService] {

  private var additionalParameters = ""

  override def getState: SnykProjectSettingsStateService = this

  override def loadState(state: SnykProjectSettingsStateService): Unit = {
    XmlSerializerUtil.copyBean(state, this)
  }

  def getAdditionalParameters: String = additionalParameters

  def setAdditionalParameters(newParameters: String): Unit = this.additionalParameters = newParameters
}

object SnykProjectSettingsStateService {
  def getInstance(project: Project): Option[SnykProjectSettingsStateService] = {
    if (isNull(project) || project.isDefault) {
      Option.empty
    } else {
      Option(ServiceManager.getService(project, classOf[SnykProjectSettingsStateService]))
    }
  }
}
