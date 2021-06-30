package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.analytics.ItlyHelper
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykTaskQueueService
import snyk.analytics.AnalysisIsTriggered

/**
 * Run scan project with Snyk action.
 */
class SnykRunScanAction : AnAction(AllIcons.Actions.Execute), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        actionEvent.project!!.service<SnykTaskQueueService>().scan()

        service<SnykAnalyticsService>().logAnalysisIsTriggered(
            AnalysisIsTriggered.builder()
                .analysisType(ItlyHelper.getSelectedProducts(getApplicationSettingsStateService()))
                .ide(AnalysisIsTriggered.Ide.JETBRAINS)
                .triggeredByUser(true)
                .build()
        )
    }

    override fun update(actionEvent: AnActionEvent) {
        val project = actionEvent.project
        if (project != null && !project.isDisposed) {
            val settings = getApplicationSettingsStateService()
            actionEvent.presentation.isEnabled =
                !isScanRunning(project) && !settings.pluginFirstRun && !settings.token.isNullOrEmpty()
        }
    }
}
