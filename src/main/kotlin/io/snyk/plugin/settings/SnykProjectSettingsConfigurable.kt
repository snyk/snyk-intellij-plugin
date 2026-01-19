package io.snyk.plugin.settings

import com.intellij.notification.Notification
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykProductsOrSeverityListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.fromUriToPath
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.getSnykToolWindowPanel
import io.snyk.plugin.isNewConfigDialogEnabled
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsync
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.SnykSettingsDialog
import io.snyk.plugin.ui.settings.HTMLSettingsPanel
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import javax.swing.JComponent

class SnykProjectSettingsConfigurable(
    val project: Project,
) : SearchableConfigurable {
    private val settingsStateService
        get() = pluginSettings()

    private var htmlSettingsPanel: HTMLSettingsPanel? = null
    private var useNewConfigDialog: Boolean = false

    var snykSettingsDialog: SnykSettingsDialog = SnykSettingsDialog(project, settingsStateService, this)

    override fun getId(): String = "io.snyk.plugin.settings.SnykProjectSettingsConfigurable"

    override fun getDisplayName(): String = "Snyk"

    override fun createComponent(): JComponent {
        useNewConfigDialog = isNewConfigDialogEnabled()
        return if (useNewConfigDialog) {
            htmlSettingsPanel = HTMLSettingsPanel(project)
            htmlSettingsPanel!!
        } else {
            snykSettingsDialog.getRootPanel()
        }
    }

    override fun isModified(): Boolean {
        if (useNewConfigDialog) {
            return htmlSettingsPanel?.isModified() ?: false
        }
        return isCoreParamsModified() ||
            isIgnoreUnknownCAModified() ||
            snykSettingsDialog.isScanTypeChanged() ||
            snykSettingsDialog.isSeverityEnablementChanged() ||
            snykSettingsDialog.isIssueViewOptionsChanged() ||
            snykSettingsDialog.manageBinariesAutomatically() != settingsStateService.manageBinariesAutomatically ||
            snykSettingsDialog.getCliPath() != settingsStateService.cliPath ||
            snykSettingsDialog.getCliBaseDownloadURL() != settingsStateService.cliBaseDownloadURL ||
            snykSettingsDialog.isScanOnSaveEnabled() != settingsStateService.scanOnSave ||
            snykSettingsDialog.getCliReleaseChannel() != settingsStateService.cliReleaseChannel ||
            snykSettingsDialog.getDisplayIssuesSelection() != settingsStateService.issuesToDisplay ||
            isAuthenticationMethodModified()
    }

    private fun isAuthenticationMethodModified() =
        snykSettingsDialog.getAuthenticationType() != settingsStateService.authenticationType

    private fun isCoreParamsModified() =
        isTokenModified() ||
            isCustomEndpointModified() ||
            isOrganizationModified() ||
            isAdditionalParametersModified() ||
            isPreferredOrgModified() ||
            isAutoSelectOrgModified() ||
            isAuthenticationMethodModified() ||
            snykSettingsDialog.isSeverityEnablementChanged() ||
            snykSettingsDialog.isIssueViewOptionsChanged() ||
            snykSettingsDialog.isScanTypeChanged()

    override fun reset() {
        if (useNewConfigDialog) {
            htmlSettingsPanel?.reset()
        } else {
            snykSettingsDialog.initializeFromSettings()
        }
    }

    override fun apply() {
        if (useNewConfigDialog) {
            htmlSettingsPanel?.apply()
            return
        }

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
        settingsStateService.authenticationType = snykSettingsDialog.getAuthenticationType()
        settingsStateService.organization = snykSettingsDialog.getOrganization()
        settingsStateService.ignoreUnknownCA = snykSettingsDialog.isIgnoreUnknownCA()

        settingsStateService.manageBinariesAutomatically = snykSettingsDialog.manageBinariesAutomatically()

        val newCliPath = snykSettingsDialog.getCliPath().trim()
        if (settingsStateService.cliPath != newCliPath) {
            settingsStateService.cliPath = newCliPath
            runBackgroundableTask("Process CLI path changes", project, true) {
                getSnykTaskQueueService(project)?.downloadLatestRelease(force = true, forceRestart = true)
            }
        }

        settingsStateService.cliBaseDownloadURL = snykSettingsDialog.getCliBaseDownloadURL().trim()

        settingsStateService.scanOnSave = snykSettingsDialog.isScanOnSaveEnabled()

        snykSettingsDialog.saveScanTypeChanges()
        snykSettingsDialog.saveSeveritiesEnablementChanges()
        snykSettingsDialog.saveIssueViewOptionsChanges()

        // Always update folder configs for auto-org settings, using the same logic as language server
        val fcs = service<FolderConfigSettings>()
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        val folderConfigs = languageServerWrapper.getWorkspaceFoldersFromRoots(project, promptForTrust = false)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { it.uri.fromUriToPath().toString() }
            .toList()

        folderConfigs.forEach { folderPath ->
            applyFolderConfigChanges(
                fcs,
                folderPath,
                snykSettingsDialog.getPreferredOrg(),
                snykSettingsDialog.isAutoSelectOrgEnabled(),
                snykSettingsDialog.getAdditionalParameters()
            )
        }

        val newCliReleaseChannel = snykSettingsDialog.getCliReleaseChannel().trim()
        if (settingsStateService.cliReleaseChannel != newCliReleaseChannel) {
            settingsStateService.cliReleaseChannel = newCliReleaseChannel
            runBackgroundableTask("Processing CLI release channel changes", project, true) {
                handleReleaseChannelChange(project)
            }
        }

        val newDisplayIssuesSelection = snykSettingsDialog.getDisplayIssuesSelection()
        if (settingsStateService.issuesToDisplay != newDisplayIssuesSelection) {
            settingsStateService.issuesToDisplay = newCliReleaseChannel
            runBackgroundableTask("Processing display issue selection changes", project, true) {
                handleDeltaFindingsChange(project)
            }
        }

        runBackgroundableTask("Execute post apply settings", project, true) {
            executePostApplySettings(project)
        }

        if (rescanNeeded) {
            getSnykToolWindowPanel(project)?.cleanUiAndCaches()
        }
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
        val storedAdditionalParams = service<FolderConfigSettings>().getAdditionalParameters(project)
        return (isProjectSettingsAvailable(project)
            && dialogAdditionalParameters != storedAdditionalParams)
    }

    private fun isPreferredOrgModified(): Boolean {
        val dialogPreferredOrg: String = snykSettingsDialog.getPreferredOrg()
        val storedPreferredOrg = service<FolderConfigSettings>().getPreferredOrg(project)
        return (isProjectSettingsAvailable(project)
            && dialogPreferredOrg != storedPreferredOrg)
    }

    private fun isAutoSelectOrgModified(): Boolean {
        val dialogAutoOrgEnabled: Boolean = snykSettingsDialog.isAutoSelectOrgEnabled()
        val storedAutoOrgEnabled = service<FolderConfigSettings>().isAutoOrganizationEnabled(project)
        return (isProjectSettingsAvailable(project) && dialogAutoOrgEnabled != storedAutoOrgEnabled)
    }
}

/**
 * Utility function to apply folder config changes based on dialog settings.
 * This encapsulates the logic for updating preferredOrg and orgSetByUser based on
 * the auto-select org checkbox state.
 */
fun applyFolderConfigChanges(
    fcs: FolderConfigSettings,
    folderPath: String,
    preferredOrgText: String,
    autoSelectOrgEnabled: Boolean,
    additionalParameters: String
) {
    val existingConfig = fcs.getFolderConfig(folderPath)

    val updatedConfig = existingConfig.copy(
        additionalParameters = additionalParameters.split(" ", System.lineSeparator()),
        // Clear the preferredOrg field if the auto org selection is enabled.
        preferredOrg = if (autoSelectOrgEnabled) "" else preferredOrgText.trim(),
        orgSetByUser = !autoSelectOrgEnabled
    )
    fcs.addFolderConfig(updatedConfig)
}

/**
 * Common post-apply logic shared between old dialog and new HTML settings panel.
 * Refreshes feature flags, updates LS configuration, and fires settings events.
 */
fun executePostApplySettings(project: Project) {
    val languageServerWrapper = LanguageServerWrapper.getInstance(project)
    val settings = pluginSettings()

    languageServerWrapper.refreshFeatureFlags()
    languageServerWrapper.updateConfiguration(true)

    settings.matchFilteringWithEnablement()

    publishAsync(project, SnykSettingsListener.SNYK_SETTINGS_TOPIC) { settingsChanged() }
    publishAsync(project, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC) { filtersChanged() }
    publishAsync(project, SnykProductsOrSeverityListener.SNYK_ENABLEMENT_TOPIC) { enablementChanged() }
}

/**
 * Handles release channel change by prompting user to download new CLI.
 * Shared between old dialog and new HTML settings panel.
 */
fun handleReleaseChannelChange(project: Project) {
    ApplicationManager.getApplication().invokeLater {
        @Suppress("CanBeVal")
        var notification: Notification? = null
        val downloadAction = object : AnAction("Download") {
            override fun actionPerformed(e: AnActionEvent) {
                getSnykTaskQueueService(project)?.downloadLatestRelease(true)
                    ?: SnykBalloonNotificationHelper.showWarn("Could not download Snyk CLI", project)
                notification?.expire()
            }
        }
        val cancelAction = object : AnAction("Cancel") {
            override fun actionPerformed(e: AnActionEvent) {
                notification?.expire()
            }
        }
        @Suppress("AssignedValueIsNeverRead")
        notification = SnykBalloonNotificationHelper.showInfo(
            "You changed the release channel. Would you like to download a new Snyk CLI now?",
            project,
            downloadAction,
            cancelAction,
        )
    }
}

/**
 * Handles delta findings (issues to display) change by clearing caches and updating tree.
 * Shared between old dialog and new HTML settings panel.
 */
fun handleDeltaFindingsChange(project: Project) {
    val cache = getSnykCachedResults(project)
    cache?.currentOSSResultsLS?.clear()
    cache?.currentSnykCodeResultsLS?.clear()
    cache?.currentIacResultsLS?.clear()
    ApplicationManager.getApplication().invokeLater {
        getSnykToolWindowPanel(project)?.getTree()?.isRootVisible = pluginSettings().isDeltaFindingsEnabled()
    }
}
