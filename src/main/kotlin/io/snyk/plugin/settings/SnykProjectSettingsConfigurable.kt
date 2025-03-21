package io.snyk.plugin.settings

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykProductsOrSeverityListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.SnykSettingsDialog
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import javax.swing.JComponent

class SnykProjectSettingsConfigurable(
    val project: Project,
) : SearchableConfigurable {
    private val settingsStateService
        get() = pluginSettings()

    var snykSettingsDialog: SnykSettingsDialog = SnykSettingsDialog(project, settingsStateService, this)

    override fun getId(): String = "io.snyk.plugin.settings.SnykProjectSettingsConfigurable"

    override fun getDisplayName(): String = "Snyk"

    override fun createComponent(): JComponent = snykSettingsDialog.getRootPanel()

    override fun isModified(): Boolean =
        isCoreParamsModified() ||
            isIgnoreUnknownCAModified() ||
            snykSettingsDialog.isScanTypeChanged() ||
            snykSettingsDialog.isSeverityEnablementChanged() ||
            snykSettingsDialog.isIssueOptionChanged() ||
            snykSettingsDialog.manageBinariesAutomatically() != settingsStateService.manageBinariesAutomatically ||
            snykSettingsDialog.getCliPath() != settingsStateService.cliPath ||
            snykSettingsDialog.getCliBaseDownloadURL() != settingsStateService.cliBaseDownloadURL ||
            snykSettingsDialog.isScanOnSaveEnabled() != settingsStateService.scanOnSave ||
            snykSettingsDialog.getCliReleaseChannel() != settingsStateService.cliReleaseChannel ||
            snykSettingsDialog.getDisplayIssuesSelection() != settingsStateService.issuesToDisplay ||

            isAuthenticationMethodModified()

    private fun isAuthenticationMethodModified() =
        snykSettingsDialog.getUseTokenAuthentication() != settingsStateService.useTokenAuthentication

    private fun isCoreParamsModified() =
        isTokenModified() ||
            isCustomEndpointModified() ||
            isOrganizationModified() ||
            isAdditionalParametersModified() ||
            isAuthenticationMethodModified() ||
            snykSettingsDialog.isSeverityEnablementChanged() ||
            snykSettingsDialog.isIssueOptionChanged() ||
            snykSettingsDialog.isScanTypeChanged()

    override fun apply() {
        val customEndpoint = snykSettingsDialog.getCustomEndpoint()

        if (snykSettingsDialog.getCliPath().isEmpty()) {
            snykSettingsDialog.setDefaultCliPath()
        }

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

        settingsStateService.manageBinariesAutomatically = snykSettingsDialog.manageBinariesAutomatically()
        settingsStateService.cliPath = snykSettingsDialog.getCliPath().trim()
        settingsStateService.cliBaseDownloadURL = snykSettingsDialog.getCliBaseDownloadURL().trim()

        settingsStateService.scanOnSave = snykSettingsDialog.isScanOnSaveEnabled()

        snykSettingsDialog.saveScanTypeChanges()
        snykSettingsDialog.saveSeveritiesEnablementChanges()
        snykSettingsDialog.saveIssueOptionChanges()

        if (isProjectSettingsAvailable(project)) {
            val fcs = service<FolderConfigSettings>()
            fcs.getAllForProject(project)
                .map {
                    it.copy(
                        additionalParameters = snykSettingsDialog.getAdditionalParameters()
                            .split(" ", System.lineSeparator())
                    )
                }
                .forEach { fcs.addFolderConfig(it) }
        }

        runBackgroundableTask("processing config changes", project, true) {
            val languageServerWrapper = LanguageServerWrapper.getInstance()
            if (snykSettingsDialog.getCliReleaseChannel().trim() != pluginSettings().cliReleaseChannel) {
                handleReleaseChannelChanged()
            }

            if (snykSettingsDialog.getDisplayIssuesSelection() != pluginSettings().issuesToDisplay) {
                settingsStateService.issuesToDisplay = snykSettingsDialog.getDisplayIssuesSelection()
                val cache = getSnykCachedResults(project)
                cache?.currentOSSResultsLS?.clear()
                cache?.currentSnykCodeResultsLS?.clear()
                cache?.currentIacResultsLS?.clear()
                getSnykToolWindowPanel(project)?.getTree()?.isRootVisible = pluginSettings().isDeltaFindingsEnabled()
            }

            languageServerWrapper.refreshFeatureFlags()
            languageServerWrapper.updateConfiguration(true)
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
    }

    private fun handleReleaseChannelChanged() {
        settingsStateService.cliReleaseChannel = snykSettingsDialog.getCliReleaseChannel().trim()
        var notification: Notification? = null
        val downloadAction = object : AnAction("Download") {
            override fun actionPerformed(e: AnActionEvent) {
                getSnykTaskQueueService(project)?.downloadLatestRelease(true) ?: SnykBalloonNotificationHelper.showWarn(
                    "Could not download Snyk CLI",
                    project
                )
                notification?.expire()
            }
        }
        val noAction = object : AnAction("Cancel") {
            override fun actionPerformed(e: AnActionEvent) {
                notification?.expire()
            }
        }
        notification = SnykBalloonNotificationHelper.showInfo(
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

    private fun isAdditionalParametersModified(): Boolean {
        val dialogAdditionalParameters: String = snykSettingsDialog.getAdditionalParameters()
        val storedAdditionalParams = service<FolderConfigSettings>().getAdditionalParams(project)
            return (isProjectSettingsAvailable(project)
                && dialogAdditionalParameters != storedAdditionalParams)
    }
}
