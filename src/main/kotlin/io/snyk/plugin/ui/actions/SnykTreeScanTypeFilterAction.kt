package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.isSnykCodeAvailable
import javax.swing.JComponent

/**
 * Build Snyk tree Severity filter (combobox) action.
 */
class SnykTreeScanTypeFilterAction : ComboBoxAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
    }

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        return DefaultActionGroup(
            listOf(
                createCliScanAction(),
                createSecurityIssuesScanAction(),
                createQualityIssuesScanAction()
            )
        )
    }

    private fun createCliScanAction(): AnAction {
        val settings = getApplicationSettingsStateService()

        return object : ToggleAction("Open Source Vulnerabilities") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.cliScanEnable

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.cliScanEnable = state
                fireFiltersChangedEvent(e.project!!)
            }
        }
    }

    private val isSnykCodeAvailable: Boolean
        get() = isSnykCodeAvailable(getApplicationSettingsStateService().customEndpointUrl)

    private fun createSecurityIssuesScanAction(): AnAction {
        val settings = getApplicationSettingsStateService()

        return object : ToggleAction("Security Issues${if (isSnykCodeAvailable) "" else " (not available)"}") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.snykCodeSecurityIssuesScanEnable

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.snykCodeSecurityIssuesScanEnable = state && isSnykCodeAvailable
                fireFiltersChangedEvent(e.project!!)
            }
        }
    }

    private fun createQualityIssuesScanAction(): AnAction {
        val settings = getApplicationSettingsStateService()

        return object : ToggleAction("Quality Issues${if (isSnykCodeAvailable) "" else " (not available)"}") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.snykCodeQualityIssuesScanEnable

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.snykCodeQualityIssuesScanEnable = state && isSnykCodeAvailable
                fireFiltersChangedEvent(e.project!!)
            }
        }
    }

    private fun fireFiltersChangedEvent(project: Project) {
        val filteringPublisher =
            project.messageBus.syncPublisher(SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)
        filteringPublisher.filtersChanged()
    }
}
