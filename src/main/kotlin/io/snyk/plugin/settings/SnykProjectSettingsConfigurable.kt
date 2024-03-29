package io.snyk.plugin.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykProductsOrSeverityListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getAmplitudeExperimentService
import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.getSnykProjectSettingsService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.net.RetrofitClientFactory
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.codeRestApi
import io.snyk.plugin.snykcode.core.SnykCodeParams
import io.snyk.plugin.snykcode.newCodeRestApi
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.SnykSettingsDialog
import org.eclipse.lsp4j.DidChangeConfigurationParams
import snyk.amplitude.api.ExperimentUser
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.toSnykCodeApiUrl
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
        snykSettingsDialog.getCliPath() != settingsStateService.cliPath ||
        snykSettingsDialog.getCliBaseDownloadURL() != settingsStateService.cliBaseDownloadURL ||
        snykSettingsDialog.isScanOnSaveEnabled() != settingsStateService.scanOnSave

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

        if (settingsStateService.customEndpointUrl != customEndpoint) {
            settingsStateService.customEndpointUrl = customEndpoint
            val snykCodeApi = toSnykCodeApiUrl(customEndpoint)
            RetrofitClientFactory.getInstance().createRetrofit(snykCodeApi)
            codeRestApi = newCodeRestApi(snykCodeApi)
        }
        settingsStateService.customEndpointUrl = customEndpoint

        settingsStateService.token = snykSettingsDialog.getToken()
        SnykCodeParams.instance.sessionToken = snykSettingsDialog.getToken()

        settingsStateService.organization = snykSettingsDialog.getOrganization()
        settingsStateService.ignoreUnknownCA = snykSettingsDialog.isIgnoreUnknownCA()

        settingsStateService.usageAnalyticsEnabled = snykSettingsDialog.isUsageAnalyticsEnabled()
        settingsStateService.crashReportingEnabled = snykSettingsDialog.isCrashReportingEnabled()

        settingsStateService.manageBinariesAutomatically = snykSettingsDialog.manageBinariesAutomatically()
        settingsStateService.cliPath = snykSettingsDialog.getCliPath().trim()
        settingsStateService.cliBaseDownloadURL = snykSettingsDialog.getCliBaseDownloadURL().trim()
        settingsStateService.scanOnSave = snykSettingsDialog.isScanOnSaveEnabled()

        snykSettingsDialog.saveScanTypeChanges()
        snykSettingsDialog.saveSeveritiesEnablementChanges()

        if (isProjectSettingsAvailable(project)) {
            val snykProjectSettingsService = getSnykProjectSettingsService(project)
            snykProjectSettingsService?.additionalParameters = snykSettingsDialog.getAdditionalParameters()
        }

        val wrapper = LanguageServerWrapper.getInstance()
        val params = DidChangeConfigurationParams(wrapper.getSettings())
        wrapper.languageServer.workspaceService.didChangeConfiguration(params)

        if (isSnykCodeLSEnabled()) {
            runBackgroundableTask("Updating Snyk Code settings", project, true) {
                settingsStateService.isGlobalIgnoresFeatureEnabled =
                    wrapper.getFeatureFlagStatus("snykCodeConsistentIgnores")
            }
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

        if (pluginSettings().usageAnalyticsEnabled) {
            runBackgroundableTask("Identifying with Analytics service", project, true) {
                val analytics = getSnykAnalyticsService()
                val userId = analytics.obtainUserId(snykSettingsDialog.getToken())
                analytics.setUserId(userId)
                getAmplitudeExperimentService().fetch(ExperimentUser(userId))
            }
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
