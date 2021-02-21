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
import javax.swing.JComponent

/**
 * Build Snyk tree Severity filter (combobox) action.
 */
class SnykTreeSeverityFilterAction : ComboBoxAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
    }

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        return DefaultActionGroup(
            listOf(
                createHighSeverityToggleAction(),
                createMediumSeverityToggleAction(),
                createLowSeverityToggleAction()
            )
        )
    }

    private fun createHighSeverityToggleAction(): AnAction {
        val settings = getApplicationSettingsStateService()

        return object : ToggleAction("High") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.highSeverityEnabled

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.highSeverityEnabled = state
                fireFiltersChangedEvent(e.project!!)
            }
        }
    }

    private fun createMediumSeverityToggleAction(): AnAction {
        val settings = getApplicationSettingsStateService()

        return object : ToggleAction("Medium") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.mediumSeverityEnabled

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.mediumSeverityEnabled = state
                fireFiltersChangedEvent(e.project!!)
            }
        }
    }

    private fun createLowSeverityToggleAction(): AnAction {
        val settings = getApplicationSettingsStateService()

        return object : ToggleAction("Low") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.lowSeverityEnabled

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.lowSeverityEnabled = state
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
