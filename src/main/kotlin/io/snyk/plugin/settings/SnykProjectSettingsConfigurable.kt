package io.snyk.plugin.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getSnykProjectSettingsService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.SnykCodeParams
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.SnykSettingsDialog
import javax.swing.JComponent

class SnykProjectSettingsConfigurable(val project: Project) : SearchableConfigurable {

    private val applicationSettingsStateService
        get() = pluginSettings()

    val snykSettingsDialog: SnykSettingsDialog =
        SnykSettingsDialog(project, applicationSettingsStateService, this)

    override fun getId(): String = "io.snyk.plugin.settings.SnykProjectSettingsConfigurable"

    override fun getDisplayName(): String = "Snyk"

    override fun createComponent(): JComponent = snykSettingsDialog.getRootPanel()

    override fun isModified(): Boolean = isCoreParamsModified() ||
        isIgnoreUnknownCAModified() ||
        isSendUsageAnalyticsModified() ||
        isCrashReportingModified() ||
        snykSettingsDialog.isScanTypeChanged() ||
        snykSettingsDialog.isSeverityEnablementChanged()

    private fun isCoreParamsModified() = isTokenModified() ||
        isCustomEndpointModified() ||
        isOrganizationModified() ||
        isAdditionalParametersModified()

    override fun apply() {
        val customEndpoint = snykSettingsDialog.getCustomEndpoint()

        if (!isUrlValid(customEndpoint)) {
            SnykBalloonNotificationHelper.showError("Invalid URL, Settings changes ignored.", project)
            return
        }

        val rescanNeeded = isCoreParamsModified()
        val productSelectionChanged = snykSettingsDialog.isScanTypeChanged()
        val severitySelectionChanged = snykSettingsDialog.isSeverityEnablementChanged()

        applicationSettingsStateService.customEndpointUrl = customEndpoint
        SnykCodeParams.instance.apiUrl = customEndpoint
        SnykCodeParams.instance.isDisableSslVerification = snykSettingsDialog.isIgnoreUnknownCA()

        applicationSettingsStateService.token = snykSettingsDialog.getToken()
        SnykCodeParams.instance.sessionToken = snykSettingsDialog.getToken()

        applicationSettingsStateService.organization = snykSettingsDialog.getOrganization()
        applicationSettingsStateService.ignoreUnknownCA = snykSettingsDialog.isIgnoreUnknownCA()
        applicationSettingsStateService.usageAnalyticsEnabled = snykSettingsDialog.isUsageAnalyticsEnabled()
        applicationSettingsStateService.crashReportingEnabled = snykSettingsDialog.isCrashReportingEnabled()
        snykSettingsDialog.saveScanTypeChanges()
        snykSettingsDialog.saveSeveritiesEnablementChanges()

        if (isProjectSettingsAvailable(project)) {
            getSnykProjectSettingsService(project)?.additionalParameters = snykSettingsDialog.getAdditionalParameters()
        }

        if (rescanNeeded) {
            getSnykToolWindowPanel(project)?.cleanUiAndCaches()
            getSyncPublisher(project, SnykSettingsListener.SNYK_SETTINGS_TOPIC)?.settingsChanged()
        }
        if (productSelectionChanged || severitySelectionChanged) {
            applicationSettingsStateService.matchFilteringWithEnablement()
            getSyncPublisher(project, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)?.filtersChanged()
        }
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

    private fun isCrashReportingModified(): Boolean =
        snykSettingsDialog.isCrashReportingEnabled() != applicationSettingsStateService.crashReportingEnabled

    private fun isAdditionalParametersModified(): Boolean = isProjectSettingsAvailable(project) &&
        snykSettingsDialog.getAdditionalParameters() != getSnykProjectSettingsService(project)?.additionalParameters
}
