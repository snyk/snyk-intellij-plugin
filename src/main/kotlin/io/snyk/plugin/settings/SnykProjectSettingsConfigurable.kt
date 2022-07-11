package io.snyk.plugin.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykProductsOrSeverityListener
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

    private val settingsStateService
        get() = pluginSettings()

    var snykSettingsDialog: SnykSettingsDialog =
        SnykSettingsDialog(project, settingsStateService, this)

    override fun getId(): String = "io.snyk.plugin.settings.SnykProjectSettingsConfigurable"

    override fun getDisplayName(): String = "Snyk"

    override fun createComponent(): JComponent = snykSettingsDialog.getRootPanel()

    override fun isModified(): Boolean = isCoreParamsModified() ||
        isIgnoreUnknownCAModified() ||
        isSendUsageAnalyticsModified() ||
        isCrashReportingModified() ||
        snykSettingsDialog.isScanTypeChanged() ||
        snykSettingsDialog.isSeverityEnablementChanged() ||
        snykSettingsDialog.manageBinariesAutomatically() != settingsStateService.manageBinariesAutomatically ||
        snykSettingsDialog.getCliPath() != settingsStateService.cliPath

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

        settingsStateService.customEndpointUrl = customEndpoint
        SnykCodeParams.instance.apiUrl = customEndpoint
        SnykCodeParams.instance.isDisableSslVerification = snykSettingsDialog.isIgnoreUnknownCA()

        settingsStateService.token = snykSettingsDialog.getToken()
        SnykCodeParams.instance.sessionToken = snykSettingsDialog.getToken()

        settingsStateService.organization = snykSettingsDialog.getOrganization()
        settingsStateService.ignoreUnknownCA = snykSettingsDialog.isIgnoreUnknownCA()

        settingsStateService.usageAnalyticsEnabled = snykSettingsDialog.isUsageAnalyticsEnabled()
        settingsStateService.crashReportingEnabled = snykSettingsDialog.isCrashReportingEnabled()

        settingsStateService.manageBinariesAutomatically = snykSettingsDialog.manageBinariesAutomatically()
        settingsStateService.cliPath = snykSettingsDialog.getCliPath().trim()

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
            settingsStateService.matchFilteringWithEnablement()
            getSyncPublisher(project, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)?.filtersChanged()
            getSyncPublisher(project, SnykProductsOrSeverityListener.SNYK_ENABLEMENT_TOPIC)?.enablementChanged()
        }
    }

    private fun isTokenModified(): Boolean =
        snykSettingsDialog.getToken() != settingsStateService.token

    private fun isCustomEndpointModified(): Boolean =
        snykSettingsDialog.getCustomEndpoint() != settingsStateService.customEndpointUrl

    private fun isOrganizationModified(): Boolean =
        snykSettingsDialog.getOrganization() != settingsStateService.organization

    private fun isIgnoreUnknownCAModified(): Boolean =
        snykSettingsDialog.isIgnoreUnknownCA() != settingsStateService.ignoreUnknownCA

    private fun isSendUsageAnalyticsModified(): Boolean =
        snykSettingsDialog.isUsageAnalyticsEnabled() != settingsStateService.usageAnalyticsEnabled

    private fun isCrashReportingModified(): Boolean =
        snykSettingsDialog.isCrashReportingEnabled() != settingsStateService.crashReportingEnabled

    private fun isAdditionalParametersModified(): Boolean = isProjectSettingsAvailable(project) &&
        snykSettingsDialog.getAdditionalParameters() != getSnykProjectSettingsService(project)?.additionalParameters
}
