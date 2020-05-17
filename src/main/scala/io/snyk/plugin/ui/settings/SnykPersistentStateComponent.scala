package io.snyk.plugin.ui.settings

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.Nullable

@State(name = "SnykPersistentState", storages = Array(new Storage("SnykPersistentState.xml")))
class SnykPersistentStateComponent
  extends PersistentStateComponent[SnykPersistentStateComponent]
          with SnykIntelliJSettings {

  var customEndpointUrl: String = _
  var organization: String = _
  var ignoreUnknownCA: Boolean = _

  override def getState: SnykPersistentStateComponent = this

  override def loadState(state: SnykPersistentStateComponent): Unit = XmlSerializerUtil.copyBean(state, this)

  override def getCustomEndpointUrl(): String = customEndpointUrl

  override def getOrganization(): String = organization

  override def isIgnoreUnknownCA(): Boolean = ignoreUnknownCA
}

object SnykPersistentStateComponent {

  @Nullable
  def getInstance(project: Project): SnykPersistentStateComponent =
    ServiceManager.getService(project, classOf[SnykPersistentStateComponent])
}
