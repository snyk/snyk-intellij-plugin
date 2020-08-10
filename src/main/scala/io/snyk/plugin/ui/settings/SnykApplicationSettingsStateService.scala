package io.snyk.plugin.ui.settings

import java.time.LocalDate
import java.util.Objects.nonNull

import com.intellij.openapi.components.{PersistentStateComponent, ServiceManager, State, Storage}
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
  name = "io.snyk.plugin.ui.settings.SnykApplicationSettingsState",
  storages = Array(new Storage("snyk.settings.xml"))
)
class SnykApplicationSettingsStateService
  extends PersistentStateComponent[SnykApplicationSettingsStateService] {

  private var customEndpointUrl = ""
  private var organization = ""
  private var ignoreUnknownCA = false
  private var cliVersion = ""
  private var lastCheckDate: LocalDate = _

  /**
    * Collect Application settings and project settings (if exists) to one object.
    *
    * @param project - current project
    *
    * @return SnykIdeSettings
    */
  def allSettings(project: Project = null): SnykIdeSettings = {
    val allSettings = SnykIdeSettings(
      customEndpointUrl,
      organization,
      ignoreUnknownCA,
      cliVersion,
      lastCheckDate,
      ""
    )

    if (nonNull(project) && !project.isDisposed) {
      val projectSettingsService = SnykProjectSettingsStateService.getInstance(project)

      if (projectSettingsService.nonEmpty) {
        allSettings.additionalParameters = projectSettingsService.get.getAdditionalParameters
      }
    }

    allSettings
  }

  override def getState: SnykApplicationSettingsStateService = this

  override def loadState(state: SnykApplicationSettingsStateService): Unit = {
    XmlSerializerUtil.copyBean(state, this)
  }

  def getCustomEndpointUrl: String = customEndpointUrl

  def setCustomEndpointUrl(newCustomEndpoint: String): Unit = this.customEndpointUrl = newCustomEndpoint

  def getOrganization: String = organization

  def setOrganization(newOrganization: String): Unit = this.organization = newOrganization

  def isIgnoreUnknownCA: Boolean = ignoreUnknownCA

  def setIgnoreUnknownCA(newIgnoreUnknownCA: Boolean): Unit = this.ignoreUnknownCA = newIgnoreUnknownCA

  def getCliVersion: String = cliVersion

  def setCliVersion(newCliVersion: String): Unit = this.cliVersion = newCliVersion

  def getLastCheckDate: LocalDate = lastCheckDate

  def setLastCheckDate(newDate: LocalDate): Unit = lastCheckDate = newDate
}

object SnykApplicationSettingsStateService {

  def apply(): SnykApplicationSettingsStateService = new SnykApplicationSettingsStateService()

  def getInstance(): SnykApplicationSettingsStateService =
    ServiceManager.getService(classOf[SnykApplicationSettingsStateService])
}


