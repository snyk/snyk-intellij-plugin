package io.snyk.plugin.ui.settings

import java.time.LocalDate

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nullable

@State(name = "SnykPersistentState", storages = Array(new Storage("SnykPersistentState.xml")))
class SnykPersistentStateComponent
  extends PersistentStateComponent[SnykIntelliJSettingsState] {

  private var snykIntelliJSettingsState = SnykIntelliJSettingsState.Empty

  override def getState: SnykIntelliJSettingsState = snykIntelliJSettingsState

  override def loadState(state: SnykIntelliJSettingsState): Unit = snykIntelliJSettingsState = state

  def customEndpointUrl: String = snykIntelliJSettingsState.customEndpointUrl

  def setCustomEndpointUrl(newEndpoint: String): Unit = snykIntelliJSettingsState.customEndpointUrl = newEndpoint

  def organization: String = snykIntelliJSettingsState.organization

  def setOrganization(newOrganization: String): Unit = snykIntelliJSettingsState.organization = newOrganization

  def isIgnoreUnknownCA: Boolean = snykIntelliJSettingsState.ignoreUnknownCA

  def setIgnoreUnknownCA(newIgnoreUnknownCA: Boolean): Unit = snykIntelliJSettingsState.ignoreUnknownCA = newIgnoreUnknownCA

  def cliVersion: String = snykIntelliJSettingsState.cliVersion

  def setCliVersion(newCliVersion: String) = snykIntelliJSettingsState.cliVersion = newCliVersion

  def lastCheckDate: LocalDate = snykIntelliJSettingsState.lastCheckDate

  def setLastCheckDate(lastCheckDate: LocalDate) = snykIntelliJSettingsState.lastCheckDate = lastCheckDate
}

object SnykPersistentStateComponent {

  def apply(): SnykPersistentStateComponent = {
    val stateComponent = new SnykPersistentStateComponent()

    stateComponent.loadState(SnykIntelliJSettingsState())

    stateComponent
  }

  def apply(
    customEndpointUrl: String = "",
    organization: String = "",
    isIgnoreUnknownCA: Boolean = false,
    cliVersion: String = "",
    lastCheckDate: LocalDate = null): SnykPersistentStateComponent = {

    val stateComponent = SnykPersistentStateComponent()

    stateComponent.setCustomEndpointUrl(customEndpointUrl)
    stateComponent.setOrganization(organization)
    stateComponent.setIgnoreUnknownCA(isIgnoreUnknownCA)
    stateComponent.setCliVersion(cliVersion)
    stateComponent.setLastCheckDate(lastCheckDate)

    stateComponent
  }

  val Empty: SnykPersistentStateComponent = {
    val snykPersistentStateComponent: SnykPersistentStateComponent = new SnykPersistentStateComponent()
    snykPersistentStateComponent.loadState(SnykIntelliJSettingsState.Empty)

    snykPersistentStateComponent
  }

  @Nullable
  def getInstance(project: Project): SnykPersistentStateComponent =
    ServiceManager.getService(project, classOf[SnykPersistentStateComponent])
}
