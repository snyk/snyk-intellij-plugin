package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.ui.SnykBalloonNotifications

abstract class SnykTreeSeverityFilterActionBase : ToggleAction() {

    protected fun isLastSeverityDisabling(e: AnActionEvent): Boolean {
        val settings = getApplicationSettingsStateService()
        val onlyOneEnabled = (settings.highSeverityEnabled && !settings.mediumSeverityEnabled && !settings.lowSeverityEnabled) ||
            (!settings.highSeverityEnabled && settings.mediumSeverityEnabled && !settings.lowSeverityEnabled) ||
            (!settings.highSeverityEnabled && !settings.mediumSeverityEnabled && settings.lowSeverityEnabled)
        if (onlyOneEnabled) {
            SnykBalloonNotifications.showWarnBalloonAtEventPlace("At least one Severity type should be selected", e)
        }
        return onlyOneEnabled
    }
}
