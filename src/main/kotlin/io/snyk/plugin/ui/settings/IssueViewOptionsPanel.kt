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

    private var currentOpenIssuesEnabled = settings.openIssuesEnabled
    private var currentIgnoredIssuesEnabled = settings.ignoredIssuesEnabled

    val panel = com.intellij.ui.dsl.builder.panel {
        row {
            checkBox(
                text = "Open issues",
            ).applyToComponent {
                name = text
            }
            .actionListener{ _, it ->
                if (canBeChanged(it, it.isSelected)) {
                    currentOpenIssuesEnabled = it.isSelected
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
                    name = text
                }
                .actionListener{ _, it ->
                    if (canBeChanged(it, it.isSelected)) {
                        currentIgnoredIssuesEnabled = it.isSelected
                        getSnykTaskQueueService(project)?.scan()
                    }
                }
                .bindSelected(settings::ignoredIssuesEnabled)
        }
    }.apply {
        name = "issueViewPanel"
        border = JBUI.Borders.empty(2)
    }

    private fun canBeChanged(component: JBCheckBox, isSelected: Boolean): Boolean {
        val onlyOneEnabled = arrayOf(
            currentOpenIssuesEnabled,
            currentIgnoredIssuesEnabled,
        ).count { it } == 1

        if (onlyOneEnabled && !isSelected) {
            SnykBalloonNotificationHelper.showWarnBalloonForComponent(
                "At least one option should be selected",
                component
            )
            component.isSelected = true
            return false
        }
        return true
    }

    fun reset() {
        currentOpenIssuesEnabled = settings.openIssuesEnabled
        currentIgnoredIssuesEnabled = settings.ignoredIssuesEnabled
        panel.reset()
    }
}
