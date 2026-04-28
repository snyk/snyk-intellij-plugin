package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsync
import io.snyk.plugin.showSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.getDisabledIcon
import snyk.common.lsp.settings.FolderConfigSettings

abstract class SnykTreeSeverityFilterActionBase(private val severity: Severity) : ToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    e.presentation.isEnabled = project != null && !project.isDisposed
    val severityIcon = SnykIcons.getSeverityIcon(severity)
    val globalEnabled = pluginSettings().hasSeverityEnabled(severity)
    val effectivelyEnabled =
      if (project != null && !project.isDisposed) {
        service<FolderConfigSettings>()
          .isSeverityEnabledForProjectToolWindow(severity, project, globalEnabled)
      } else {
        globalEnabled
      }
    if (effectivelyEnabled) {
      e.presentation.icon = severityIcon
      e.presentation.text = severity.toPresentableString()
    } else {
      e.presentation.icon = getDisabledIcon(severityIcon)
      e.presentation.text = "Disabled in Settings"
    }
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project ?: return pluginSettings().hasSeverityEnabled(severity)
    if (project.isDisposed) return pluginSettings().hasSeverityEnabled(severity)
    val globalEnabled = pluginSettings().hasSeverityEnabled(severity)
    return service<FolderConfigSettings>()
      .isSeverityEnabledForProjectToolWindow(severity, project, globalEnabled)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project!!
    val globalEnabled = pluginSettings().hasSeverityEnabled(severity)
    val effectivelyEnabled =
      service<FolderConfigSettings>()
        .isSeverityEnabledForProjectToolWindow(severity, project, globalEnabled)
    if (!effectivelyEnabled) {
      showSettings(
        project = project,
        componentNameToFocus = severity.toPresentableString(),
        componentHelpHint = "Enable severity level here",
      )
      return
    }
    if (!state && isLastSeverityDisabling(e)) return

    pluginSettings().setSeverityTreeFiltered(severity, state)
    publishAsync(e.project!!, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC) {
      filtersChanged()
    }
  }

  private fun isLastSeverityDisabling(e: AnActionEvent): Boolean {
    val project = e.project ?: return false
    val onlyOneEnabled = pluginSettings().hasOnlyOneSeverityTreeFilterActive(project)
    if (onlyOneEnabled) {
      SnykBalloonNotificationHelper.showWarnBalloonAtEventPlace(
        "At least one Severity type should be selected",
        e,
      )
    }
    return onlyOneEnabled
  }
}

class SnykTreeLowSeverityFilterAction : SnykTreeSeverityFilterActionBase(Severity.LOW)

class SnykTreeMediumSeverityFilterAction : SnykTreeSeverityFilterActionBase(Severity.MEDIUM)

class SnykTreeHighSeverityFilterAction : SnykTreeSeverityFilterActionBase(Severity.HIGH)

class SnykTreeCriticalSeverityFilterAction : SnykTreeSeverityFilterActionBase(Severity.CRITICAL)
