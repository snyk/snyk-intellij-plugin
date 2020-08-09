package io.snyk.plugin.ui.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

import io.snyk.plugin.Utils.isUrlValid

class SnykProjectSettingsConfigurable(project: Project) extends SearchableConfigurable {

  private val applicationSettingsStateService: SnykApplicationSettingsStateService =
    SnykApplicationSettingsStateService.getInstance()

  private val projectSettingsStateService: Option[SnykProjectSettingsStateService] =
    SnykProjectSettingsStateService.getInstance(project)

  private val settingsDialog: SettingsDialog =
    new SettingsDialog(applicationSettingsStateService, projectSettingsStateService)

  override def getId: String = "io.snyk.plugin.ui.settings.SnykProjectSettingsConfigurable"

  override def getDisplayName: String = "Snyk project"

  override def createComponent(): JComponent = {
    settingsDialog.getRootPanel
  }

  override def isModified: Boolean =
    isCustomEndpointModified || isOrganizationModified || isIgnoreUnknownCAModified || isAdditionalParametersModified

  override def apply(): Unit = {
    val customEndpoint = settingsDialog.customEndpoint

    if (!isUrlValid(customEndpoint)) {
      return
    }

    applicationSettingsStateService.setCustomEndpointUrl(customEndpoint)
    applicationSettingsStateService.setOrganization(settingsDialog.organization)
    applicationSettingsStateService.setIgnoreUnknownCA(settingsDialog.isIgnoreUnknownCA)

    if (projectSettingsStateService.nonEmpty) {
      projectSettingsStateService.get.setAdditionalParameters(settingsDialog.additionalParameters)
    }
  }

  private def isCustomEndpointModified =
    settingsDialog.customEndpoint != applicationSettingsStateService.getCustomEndpointUrl

  private def isOrganizationModified =
    settingsDialog.organization != applicationSettingsStateService.getOrganization

  private def isIgnoreUnknownCAModified = false
      settingsDialog.isIgnoreUnknownCA != applicationSettingsStateService.isIgnoreUnknownCA

  private def isAdditionalParametersModified =
    projectSettingsStateService.nonEmpty && settingsDialog.additionalParameters != projectSettingsStateService.get.getAdditionalParameters
}
