package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import javax.swing.JComponent

/**
 * Build Snyk tree Severity filter (combobox) action.
 */
class SnykTreeScanTypeFilterAction : ComboBoxAction(), DumbAware {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
        e.presentation.text = "Scan for issue types:"
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
                e.project?.service<SnykToolWindowPanel>()?.cleanAll()
            }
        }
    }

    private fun createSecurityIssuesScanAction(): AnAction {
        val settings = getApplicationSettingsStateService()

        return object : ToggleAction("Security Issues") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.snykCodeScanEnable

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.snykCodeScanEnable = state
                e.project?.service<SnykToolWindowPanel>()?.cleanAll()
            }
        }
    }

    private fun createQualityIssuesScanAction(): AnAction {
        val settings = getApplicationSettingsStateService()

        return object : ToggleAction("Quality Issues") {
            override fun isSelected(e: AnActionEvent): Boolean = settings.snykCodeQualityIssuesScanEnable

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                settings.snykCodeQualityIssuesScanEnable = state
                e.project?.service<SnykToolWindowPanel>()?.cleanAll()
            }
        }
    }
}
