package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.publishAsync
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.showSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.getDisabledIcon
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings

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
        val fcs = service<FolderConfigSettings>()
        val enabled = if (project != null && !project.isDisposed) fcs.hasSeverityEnabled(project, severity) else pluginSettings().hasSeverityEnabled(severity)
        if (enabled) {
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
        val project = e.project ?: return
        val fcs = service<FolderConfigSettings>()
        if (!fcs.hasSeverityEnabled(project, severity)) {
            showSettings(
                project = project,
                componentNameToFocus = severity.toPresentableString(),
                componentHelpHint = "Enable severity level here"
            )
            return
        }
        if (!state && isLastSeverityDisabling(e)) return

        pluginSettings().setSeverityTreeFiltered(severity, state)

        val currentSeverities = fcs.let {
            mapOf(
                "critical" to if (severity == Severity.CRITICAL) state else it.isCriticalSeverityEnabled(project),
                "high" to if (severity == Severity.HIGH) state else it.isHighSeverityEnabled(project),
                "medium" to if (severity == Severity.MEDIUM) state else it.isMediumSeverityEnabled(project),
                "low" to if (severity == Severity.LOW) state else it.isLowSeverityEnabled(project),
            )
        }
        val lsWrapper = LanguageServerWrapper.getInstance(project)
        val folderConfigs = fcs.getFolderConfigs(project)
        for (fc in folderConfigs) {
            lsWrapper.sendFolderConfigPatch(fc.folderPath, mapOf("enabledSeverities" to currentSeverities))
        }

        publishAsync(project, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC) { filtersChanged() }
    }

    private fun isLastSeverityDisabling(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val fcs = service<FolderConfigSettings>()
        val count = arrayOf(
            fcs.hasSeverityEnabledAndFiltered(project, Severity.CRITICAL),
            fcs.hasSeverityEnabledAndFiltered(project, Severity.HIGH),
            fcs.hasSeverityEnabledAndFiltered(project, Severity.MEDIUM),
            fcs.hasSeverityEnabledAndFiltered(project, Severity.LOW),
        ).count { it }
        val onlyOneEnabled = count == 1
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
