package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isSnykCodeAvailable
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.ui.SnykBalloonNotifications
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import javax.swing.JComponent

/**
 * Build Snyk tree Severity filter (combobox) action.
 */
class SnykTreeScanTypeFilterAction : ComboBoxAction() {

    private val settings = getApplicationSettingsStateService()

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
    }

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        return DefaultActionGroup(
            listOf(
                createOssScanAction(),
                createSecurityIssuesScanAction(),
                createQualityIssuesScanAction(),
                createIacScanAction(),
                createContainerScanAction()
            )
        )
    }

    private fun createOssScanAction(): AnAction {
        return object : ToggleAction("Open Source Vulnerabilities") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.ossScanEnable

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (!state && isLastScanTypeDisabling(e)) return

                settings.ossScanEnable = state
                fireFiltersChangedEvent(e.project!!)
            }
        }
    }

    private fun createIacScanAction(): AnAction {
        return object : ToggleAction("Infrastructure as Code Vulnerabilities") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.iacScanEnabled

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (!state && isLastScanTypeDisabling(e)) return

                settings.iacScanEnabled = state
                fireFiltersChangedEvent(e.project!!)
            }
        }
    }

    private fun createContainerScanAction(): AnAction {
        return object : ToggleAction("Container Security Issues") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.containerScanEnabled

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (!state && isLastScanTypeDisabling(e)) return

                settings.containerScanEnabled = state
                fireFiltersChangedEvent(e.project!!)
            }
        }
    }

    private fun isSnykCodeAvailable(): Boolean =
        isSnykCodeAvailable(settings.customEndpointUrl) && (settings.sastOnServerEnabled ?: false)

    private fun showSettings(project: Project) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, SnykProjectSettingsConfigurable::class.java)
    }

    private fun createSecurityIssuesScanAction(): AnAction {
        return object : ToggleAction("Security Issues${snykCodeAvailabilityPostfix()}") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.snykCodeSecurityIssuesScanEnable

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (!state && isLastScanTypeDisabling(e)) return

                val available = isSnykCodeAvailable()
                settings.snykCodeSecurityIssuesScanEnable = state && available
                fireFiltersChangedEvent(e.project!!)
                if (!available) showSettings(e.project!!)
            }
        }
    }

    private fun createQualityIssuesScanAction(): AnAction {
        return object : ToggleAction("Quality Issues${snykCodeAvailabilityPostfix()}") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.snykCodeQualityIssuesScanEnable

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (!state && isLastScanTypeDisabling(e)) return

                val available = isSnykCodeAvailable()
                settings.snykCodeQualityIssuesScanEnable = state && available
                fireFiltersChangedEvent(e.project!!)
                if (!available) showSettings(e.project!!)
            }
        }
    }

    private fun fireFiltersChangedEvent(project: Project) {
        getSyncPublisher(project, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)?.filtersChanged()
    }

    private fun isLastScanTypeDisabling(e: AnActionEvent): Boolean {
        val onlyOneEnabled = arrayOf(
            settings.ossScanEnable,
            settings.snykCodeSecurityIssuesScanEnable,
            settings.snykCodeQualityIssuesScanEnable,
            settings.iacScanEnabled && !settings.containerScanEnabled,
            settings.containerScanEnabled
        ).count { it } == 1
        if (onlyOneEnabled) {
            SnykBalloonNotifications.showWarnBalloonAtEventPlace("At least one Scan type should be selected", e)
        }
        return onlyOneEnabled
    }
}
