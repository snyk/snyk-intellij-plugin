package io.snyk.plugin.ui.settings

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
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
            ).component.apply {
                isSelected = settings.openIssuesEnabled
                this.addItemListener {
                    if (canOptionChange(this, settings.openIssuesEnabled)) {
                        settings.openIssuesEnabled = this.isSelected
                        getSnykTaskQueueService(project)?.scan()
                    }
                }
                name = "Open issues"
            }
        }
        row {
                checkBox(
                    text = "Ignored issues",
                ).component.apply {
                    isSelected = settings.ignoredIssuesEnabled
                    this.addItemListener {
                        if (canOptionChange(this, settings.ignoredIssuesEnabled)) {
                            settings.ignoredIssuesEnabled = this.isSelected
                            getSnykTaskQueueService(project)?.scan()
                        }
                    }
                    name = "Ignored issues"
                }
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
