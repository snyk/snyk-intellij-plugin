package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isContainerEnabled
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import javax.swing.JComponent

/**
 * Build Snyk tree Severity filter (combobox) action.
 */
class SnykTreeScanTypeFilterAction : ComboBoxAction() {

    private val settings
        get() = pluginSettings()

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed && !settings.token.isNullOrEmpty()
    }

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        return DefaultActionGroup(
            listOfNotNull(
                createOssScanAction(),
                createSecurityIssuesScanAction(),
                createQualityIssuesScanAction(),
                if (isIacEnabled()) createIacScanAction() else null,
                if (isContainerEnabled()) createContainerScanAction() else null
            )
        )
    }

    private fun availabilityPostfix(productEnabled: Boolean): String =
        if (productEnabled) "" else " (disabled)"

    private fun isSnykCodeAvailable(): Boolean = snykCodeAvailabilityPostfix().isEmpty()

    private fun showSettings(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, SnykProjectSettingsConfigurable::class.java)
    }

    private fun createScanFilteringAction(
        scanTypeTitle: String,
        scanTypeEnabled: Boolean,
        resultsTreeFiltering: Boolean,
        setResultsTreeFiltering: (Boolean) -> Unit
    ): AnAction =
        object : ToggleAction(scanTypeTitle + availabilityPostfix(scanTypeEnabled)) {
            override fun isSelected(e: AnActionEvent): Boolean = resultsTreeFiltering

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (!scanTypeEnabled) {
                    showSettings(e.project!!)
                    return
                }
                if (!state && isLastScanTypeDisabling(e)) return

                setResultsTreeFiltering(state)
                getSyncPublisher(e.project!!, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)?.filtersChanged()
            }
        }

    private fun createOssScanAction(): AnAction = createScanFilteringAction(
        scanTypeTitle = "Open Source Vulnerabilities",
        scanTypeEnabled = settings.ossScanEnable,
        resultsTreeFiltering = settings.ossResultsTreeFiltering,
        setResultsTreeFiltering = { settings.ossResultsTreeFiltering = it }
    )

    private fun createSecurityIssuesScanAction(): AnAction = createScanFilteringAction(
        scanTypeTitle = "Security Issues",
        scanTypeEnabled = settings.snykCodeSecurityIssuesScanEnable && isSnykCodeAvailable(),
        resultsTreeFiltering = settings.codeSecurityResultsTreeFiltering,
        setResultsTreeFiltering = { settings.codeSecurityResultsTreeFiltering = it }
    )

    private fun createQualityIssuesScanAction(): AnAction = createScanFilteringAction(
        scanTypeTitle = "Quality Issues",
        scanTypeEnabled = settings.snykCodeQualityIssuesScanEnable && isSnykCodeAvailable(),
        resultsTreeFiltering = settings.codeQualityResultsTreeFiltering,
        setResultsTreeFiltering = { settings.codeQualityResultsTreeFiltering = it }
    )

    private fun createIacScanAction(): AnAction = createScanFilteringAction(
        scanTypeTitle = "Configuration Issues",
        scanTypeEnabled = settings.iacScanEnabled,
        resultsTreeFiltering = settings.iacResultsTreeFiltering,
        setResultsTreeFiltering = { settings.iacResultsTreeFiltering = it }
    )

    private fun createContainerScanAction(): AnAction = createScanFilteringAction(
        scanTypeTitle = "Container Vulnerabilities",
        scanTypeEnabled = settings.containerScanEnabled,
        resultsTreeFiltering = settings.containerResultsTreeFiltering,
        setResultsTreeFiltering = { settings.containerResultsTreeFiltering = it }
    )

    private fun isLastScanTypeDisabling(e: AnActionEvent): Boolean {
        val onlyOneEnabled = arrayOf(
            settings.ossScanEnable && settings.ossResultsTreeFiltering,
            settings.snykCodeSecurityIssuesScanEnable && settings.codeSecurityResultsTreeFiltering,
            settings.snykCodeQualityIssuesScanEnable && settings.codeQualityResultsTreeFiltering,
            settings.iacScanEnabled && settings.iacResultsTreeFiltering,
            settings.containerScanEnabled && settings.containerResultsTreeFiltering
        ).count { it } == 1
        if (onlyOneEnabled) {
            val message = "At least one Scan type should be enabled and selected"
            SnykBalloonNotificationHelper.showWarnBalloonAtEventPlace(message, e)
        }
        return onlyOneEnabled
    }
}
