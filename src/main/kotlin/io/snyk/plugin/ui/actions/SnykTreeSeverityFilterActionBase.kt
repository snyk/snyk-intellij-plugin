package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper

abstract class SnykTreeSeverityFilterActionBase : ToggleAction() {

    protected fun isLastSeverityDisabling(e: AnActionEvent): Boolean {
        val settings = pluginSettings()

        val onlyOneEnabled = arrayOf(
            settings.criticalSeverityEnabled,
            settings.highSeverityEnabled,
            settings.mediumSeverityEnabled,
            settings.lowSeverityEnabled
        ).count { it } == 1

        if (onlyOneEnabled) {
            SnykBalloonNotificationHelper.showWarnBalloonAtEventPlace(
                "At least one Severity type should be selected",
                e
            )
        }

        return onlyOneEnabled
    }
}
