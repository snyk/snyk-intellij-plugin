package io.snyk.plugin.settings

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykProductsOrSeverityListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getAmplitudeExperimentService
import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.getSnykProjectSettingsService
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.SnykSettingsDialog
import snyk.amplitude.api.ExperimentUser
import snyk.common.lsp.LanguageServerWrapper
import javax.swing.JComponent

class SnykProjectSettingsConfigurable(
    val project: Project,
) : SearchableConfigurable {
    private val settingsStateService
        get() = pluginSettings()

    var snykSettingsDialog: SnykSettingsDialog =
        SnykSettingsDialog(project, settingsStateService, this)

    override fun getId(): String = "io.snyk.plugin.settings.SnykProjectSettingsConfigurable"

    override fun getDisplayName(): String = "Snyk"

    override fun createComponent(): JComponent = snykSettingsDialog.getRootPanel()

    override fun isModified(): Boolean =
        isCoreParamsModified() ||
            isIgnoreUnknownCAModified() ||
            isSendUsageAnalyticsModified() ||
            isCrashReportingModified() ||
            snykSettingsDialog.isScanTypeChanged() ||
            snykSettingsDialog.isSeverityEnablementChanged() ||
            snykSettingsDialog.isIssueOptionChanged() ||
            snykSettingsDialog.manageBinariesAutomatically() != settingsStateService.manageBinariesAutomatically ||
            snykSettingsDialog.getCliPath() != settingsStateService.cliPath ||
            snykSettingsDialog.getCliBaseDownloadURL() != settingsStateService.cliBaseDownloadURL ||
            snykSettingsDialog.isScanOnSaveEnabled() != settingsStateService.scanOnSave ||
            snykSettingsDialog.getCliReleaseChannel() != settingsStateService.cliReleaseChannel

    private fun isCoreParamsModified() =
        isTokenModified() ||
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

        if (isCustomEndpointModified()) {
            settingsStateService.customEndpointUrl = customEndpoint
        }

        settingsStateService.token = snykSettingsDialog.getToken()
        settingsStateService.useTokenAuthentication = snykSettingsDialog.getUseTokenAuthentication()
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
        snykSettingsDialog.saveIssueOptionChanges()

        if (isProjectSettingsAvailable(project)) {
            val snykProjectSettingsService = getSnykProjectSettingsService(project)
            snykProjectSettingsService?.additionalParameters = snykSettingsDialog.getAdditionalParameters()
        }

        runBackgroundableTask("Updating Snyk Code settings", project, true) {
            settingsStateService.isGlobalIgnoresFeatureEnabled =
                LanguageServerWrapper.getInstance().getFeatureFlagStatus("snykCodeConsistentIgnores")
        }

        if (snykSettingsDialog.getCliReleaseChannel().trim() != pluginSettings().cliReleaseChannel) {
            handleReleaseChannelChanged()
        }

        if (rescanNeeded) {
            getSnykToolWindowPanel(project)?.cleanUiAndCaches()
            // FIXME we should always send settings updates, and listeners should decide what to do
            // A settings change should not cause a scan automatically, so the event should be split
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

    private fun handleReleaseChannelChanged() {
        settingsStateService.cliReleaseChannel = snykSettingsDialog.getCliReleaseChannel().trim()
        var notification: Notification? = null
        val downloadAction =
            object : AnAction("Download") {
                override fun actionPerformed(e: AnActionEvent) {
                    getSnykTaskQueueService(project)?.downloadLatestRelease(true)
                        ?: SnykBalloonNotificationHelper.showWarn("Could not download Snyk CLI", project)
                    notification?.expire()
                }
            }
        val noAction =
            object : AnAction("Cancel") {
                override fun actionPerformed(e: AnActionEvent) {
                    notification?.expire()
                }
            }
        notification =
            SnykBalloonNotificationHelper.showInfo(
                "You changed the release channel. Would you like to download a new Snyk CLI now?",
                project,
                downloadAction,
                noAction,
            )
    }

    private fun isTokenModified(): Boolean = snykSettingsDialog.getToken() != settingsStateService.token

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

    private fun isAdditionalParametersModified(): Boolean =
        isProjectSettingsAvailable(project) &&
            snykSettingsDialog.getAdditionalParameters() != getSnykProjectSettingsService(project)?.additionalParameters
}
