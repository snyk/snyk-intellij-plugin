package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.showSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.getDisabledIcon

abstract class SnykTreeSeverityFilterActionBase(
    private val severity: Severity
) : ToggleAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
        val severityIcon = SnykIcons.getSeverityIcon(severity)
        if (pluginSettings().hasSeverityEnabled(severity)) {
            e.presentation.icon = severityIcon
            e.presentation.text = severity.toPresentableString()
        } else {
            e.presentation.icon = getDisabledIcon(severityIcon)
            e.presentation.text = "Disabled in Settings"
        }
    }

    override fun isSelected(e: AnActionEvent): Boolean =
        pluginSettings().hasSeverityTreeFiltered(severity)

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (!pluginSettings().hasSeverityEnabled(severity)) {
            showSettings(
                project = e.project!!,
                componentNameToFocus = severity.toPresentableString(),
                componentHelpHint = "Enable severity level here"
            )
            return
        }
        if (!state && isLastSeverityDisabling(e)) return

        pluginSettings().setSeverityTreeFiltered(severity, state)
        getSyncPublisher(e.project!!, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)?.filtersChanged()
    }

    private fun isLastSeverityDisabling(e: AnActionEvent): Boolean {
        val onlyOneEnabled = pluginSettings().hasOnlyOneSeverityEnabled()
        if (onlyOneEnabled) {
            SnykBalloonNotificationHelper.showWarnBalloonAtEventPlace(
                "At least one Severity type should be selected",
                e
            )
        }
        return onlyOneEnabled
    }
}

class SnykTreeLowSeverityFilterAction : SnykTreeSeverityFilterActionBase(Severity.LOW)

class SnykTreeMediumSeverityFilterAction : SnykTreeSeverityFilterActionBase(Severity.MEDIUM)

class SnykTreeHighSeverityFilterAction : SnykTreeSeverityFilterActionBase(Severity.HIGH)

class SnykTreeCriticalSeverityFilterAction : SnykTreeSeverityFilterActionBase(Severity.CRITICAL)
