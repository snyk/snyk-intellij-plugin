package io.snyk.plugin.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykProjectSettingsStateService
import io.snyk.plugin.ui.SettingsDialog
import javax.swing.JComponent

class SnykProjectSettingsConfigurable(val project: Project) : SearchableConfigurable {

    private val applicationSettingsStateService: SnykApplicationSettingsStateService =
        getApplicationSettingsStateService()

    private val settingsDialog: SettingsDialog =
        SettingsDialog(project, applicationSettingsStateService)

    override fun getId(): String = "io.snyk.plugin.settings.SnykProjectSettingsConfigurable"

    override fun getDisplayName(): String = "Snyk"

    override fun createComponent(): JComponent = settingsDialog.getRootPanel()

    override fun isModified(): Boolean = isCustomEndpointModified()
        || isOrganizationModified()
        || isIgnoreUnknownCAModified()
        || isAdditionalParametersModified()

    override fun apply() {
        val customEndpoint = settingsDialog.getCustomEndpoint()

        if (!isUrlValid(customEndpoint)) {
            return
        }

        applicationSettingsStateService.setCustomEndpointUrl(customEndpoint)
        applicationSettingsStateService.setOrganization(settingsDialog.getOrganization())
        applicationSettingsStateService.setIgnoreUnknownCA(settingsDialog.isIgnoreUnknownCA())

        if (isProjectSettingsAvailable(project)) {
            project.service<SnykProjectSettingsStateService>().setAdditionalParameters(settingsDialog.getAdditionalParameters())
        }
    }

    private fun isCustomEndpointModified(): Boolean =
        settingsDialog.getCustomEndpoint() != applicationSettingsStateService.getCustomEndpointUrl()

    private fun isOrganizationModified(): Boolean =
        settingsDialog.getOrganization() != applicationSettingsStateService.getOrganization()

    private fun isIgnoreUnknownCAModified(): Boolean =
        settingsDialog.isIgnoreUnknownCA() != applicationSettingsStateService.isIgnoreUnknownCA()

    private fun isAdditionalParametersModified(): Boolean =
        isProjectSettingsAvailable(project)
            && settingsDialog.getAdditionalParameters() != project.service<SnykProjectSettingsStateService>().getAdditionalParameters()
}
