package io.snyk.plugin.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykProjectSettingsStateService
import io.snyk.plugin.snykcode.core.SnykCodeParams
import io.snyk.plugin.toSnykCodeApiUrl
import io.snyk.plugin.ui.SnykSettingsDialog
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import javax.swing.JComponent

class SnykProjectSettingsConfigurable(val project: Project) : SearchableConfigurable {

    private val applicationSettingsStateService: SnykApplicationSettingsStateService =
        getApplicationSettingsStateService()

    private val snykSettingsDialog: SnykSettingsDialog =
        SnykSettingsDialog(project, applicationSettingsStateService, this)

    override fun getId(): String = "io.snyk.plugin.settings.SnykProjectSettingsConfigurable"

    override fun getDisplayName(): String = "Snyk"

    override fun createComponent(): JComponent = snykSettingsDialog.getRootPanel()

    override fun isModified(): Boolean = isTokenModified()
        || isCustomEndpointModified()
        || isOrganizationModified()
        || isIgnoreUnknownCAModified()
        || isSendUsageAnalyticsModified()
        || isAdditionalParametersModified()
        || snykSettingsDialog.isScanTypeChanged()

    override fun apply() {
        val customEndpoint = snykSettingsDialog.getCustomEndpoint()

        if (!isUrlValid(customEndpoint)) {
            return
        }

        applicationSettingsStateService.customEndpointUrl = customEndpoint
        SnykCodeParams.instance.apiUrl = toSnykCodeApiUrl(customEndpoint)

        applicationSettingsStateService.token = snykSettingsDialog.getToken()
        SnykCodeParams.instance.sessionToken = snykSettingsDialog.getToken()

        applicationSettingsStateService.organization = snykSettingsDialog.getOrganization()
        applicationSettingsStateService.ignoreUnknownCA = snykSettingsDialog.isIgnoreUnknownCA()
        applicationSettingsStateService.usageAnalyticsEnabled = snykSettingsDialog.isUsageAnalyticsEnabled()
        snykSettingsDialog.saveScanTypeChanges()

        if (isProjectSettingsAvailable(project)) {
            project.service<SnykProjectSettingsStateService>().additionalParameters = snykSettingsDialog.getAdditionalParameters()
        }

        project.service<SnykToolWindowPanel>().cleanUiAndCaches()
        project.messageBus.syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC).checkCliExistsFinished()
    }

    private fun isTokenModified(): Boolean =
        snykSettingsDialog.getToken() != applicationSettingsStateService.token

    private fun isCustomEndpointModified(): Boolean =
        snykSettingsDialog.getCustomEndpoint() != applicationSettingsStateService.customEndpointUrl

    private fun isOrganizationModified(): Boolean =
        snykSettingsDialog.getOrganization() != applicationSettingsStateService.organization

    private fun isIgnoreUnknownCAModified(): Boolean =
        snykSettingsDialog.isIgnoreUnknownCA() != applicationSettingsStateService.ignoreUnknownCA

    private fun isSendUsageAnalyticsModified(): Boolean =
        snykSettingsDialog.isUsageAnalyticsEnabled() != applicationSettingsStateService.usageAnalyticsEnabled

    private fun isAdditionalParametersModified(): Boolean =
        isProjectSettingsAvailable(project)
            && snykSettingsDialog.getAdditionalParameters() != project.service<SnykProjectSettingsStateService>().additionalParameters
}
