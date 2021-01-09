package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.ui.LafIconLookup
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import javax.swing.JComponent

/**
 * Build Snyk tree Severity filter (combobox) action.
 */
class SnykTreeSeverityFilterAction : ComboBoxAction(), DumbAware {

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
        e.presentation.text = presentation(getApplicationSettingsStateService().filterMinimalSeverity)
    }

    private fun presentation(severity: String): String = when (severity) {
        "high" -> "High severity only"
        "medium" -> "Medium and High severities"
        else -> "All severities"
    }

    override fun createPopupActionGroup(button: JComponent?): DefaultActionGroup {
        return DefaultActionGroup(
            listOf(
                createSeverityAction("low"),
                createSeverityAction("medium"),
                createSeverityAction("high")
            )
        )
    }

    private fun createSeverityAction(severity: String): AnAction {
        val icon = if (severity == getApplicationSettingsStateService().filterMinimalSeverity) {
            try {
                LafIconLookup.getIcon("checkmark")
            } catch (e: RuntimeException) {
                AllIcons.Actions.Checked
            }
        } else null
        return object : DumbAwareAction(presentation(severity), null, icon) {
            override fun actionPerformed(e: AnActionEvent) {
                getApplicationSettingsStateService().filterMinimalSeverity = severity
                e.project?.service<SnykToolWindowPanel>()?.cleanAll()
            }
        }
    }
}
