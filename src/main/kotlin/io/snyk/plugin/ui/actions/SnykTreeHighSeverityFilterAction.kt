package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.getSyncPublisher

class SnykTreeHighSeverityFilterAction: SnykTreeSeverityFilterActionBase() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed
        e.presentation.icon = SnykIcons.getSeverityIcon(Severity.HIGH)
    }

    override fun isSelected(e: AnActionEvent): Boolean =
        pluginSettings().highSeverityEnabled

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (!state && isLastSeverityDisabling(e)) return

        pluginSettings().highSeverityEnabled = state
        getSyncPublisher(e.project!!, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)?.filtersChanged()
    }
}
