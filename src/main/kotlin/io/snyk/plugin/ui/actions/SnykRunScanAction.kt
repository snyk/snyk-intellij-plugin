package io.snyk.plugin.ui.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import io.snyk.plugin.analytics.getSelectedProducts
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykAnalyticsService
import snyk.analytics.AnalysisIsTriggered

/**
 * Run scan project with Snyk action.
 */
class SnykRunScanAction : AnAction(AllIcons.Actions.Execute), DumbAware {

    override fun actionPerformed(actionEvent: AnActionEvent) {
        getSnykTaskQueueService(actionEvent.project!!)?.scan()

        service<SnykAnalyticsService>().logAnalysisIsTriggered(
            AnalysisIsTriggered.builder()
                .analysisType(getSelectedProducts(pluginSettings()))
                .ide(AnalysisIsTriggered.Ide.JETBRAINS)
                .triggeredByUser(true)
                .build()
        )
    }

    override fun update(actionEvent: AnActionEvent) {
        val project = actionEvent.project
        if (project != null && !project.isDisposed) {
            val settings = pluginSettings()
            actionEvent.presentation.isEnabled =
                !isCliDownloading() &&
                    !isScanRunning(project) &&
                    !settings.pluginFirstRun &&
                    !settings.token.isNullOrEmpty()
        }
    }
}
