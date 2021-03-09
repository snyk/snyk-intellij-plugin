package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import icons.SnykIcons
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.getApplicationSettingsStateService

class SnykTreeMediumSeverityFilterAction: SnykTreeSeverityFilterActionBase() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
        e.presentation.icon = SnykIcons.getSeverityIcon("medium")
    }

    override fun isSelected(e: AnActionEvent): Boolean =
        getApplicationSettingsStateService().mediumSeverityEnabled

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (!state && isLastSeverityDisabling(e)) return

        getApplicationSettingsStateService().mediumSeverityEnabled = state
        e.project!!.messageBus.syncPublisher(SnykResultsFilteringListener.SNYK_FILTERING_TOPIC).filtersChanged()
    }
}
