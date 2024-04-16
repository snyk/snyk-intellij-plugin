package io.snyk.plugin.ui.settings

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.ui.JBUI
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper

class IssueViewOptionsPanel(
    private val project: Project,
) {
    private val settings
        get() = pluginSettings()

    val panel = com.intellij.ui.dsl.builder.panel {
        row {
            checkBox(
                text = "Open issues",
            ).applyToComponent {
                isSelected = settings.openIssuesEnabled
                this.addItemListener {
                    if (canOptionChange(this, settings.openIssuesEnabled)) {
                        settings.openIssuesEnabled = this.isSelected
                        getSnykTaskQueueService(project)?.scan()
                    }
                }
                name = "Open issues"
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                if (canOptionChange(it, !hasBeenSelected)) {
                    // we need to change the settings in here in order for the validation to work pre-apply
                    settings.openIssuesEnabled = hasBeenSelected
                    getSnykTaskQueueService(project)?.scan()
                }
            }
            // bindSelected is needed to trigger apply() on the settings dialog that this panel is rendered in
            // that way we trigger the re-rendering of the Tree Nodes
            .bindSelected(settings::openIssuesEnabled)
        }
        row {
                checkBox(
                    text = "Ignored issues",
                ).applyToComponent {
                    name = "Ignored issues"
                }
                .actionListener{ event, it ->
                    val hasBeenSelected = it.isSelected
                    if (canOptionChange(it, !hasBeenSelected)) {
                        settings.ignoredIssuesEnabled = hasBeenSelected
                        getSnykTaskQueueService(project)?.scan()
                    }
                }
                .bindSelected(settings::ignoredIssuesEnabled)
        }
    }.apply {
        name = "issueViewPanel"
        border = JBUI.Borders.empty(2)
    }

    private fun canOptionChange(component: JBCheckBox, wasEnabled: Boolean): Boolean {
        val onlyOneEnabled = arrayOf(
            settings.openIssuesEnabled,
            settings.ignoredIssuesEnabled,
        ).count { it } == 1

        if (onlyOneEnabled && wasEnabled) {
            component.isSelected = true
            SnykBalloonNotificationHelper.showWarnBalloonForComponent(
                "At least one option should be selected",
                component
            )
            return false
        }
        return true
    }
}
