package io.snyk.plugin.ui.settings

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
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

    val panel = panel {
        row {
            cell {
                checkBox(
                    text = "Open issues",
                    getter = { settings.openIssuesEnabled },
                    setter = { settings.openIssuesEnabled = it }
                ).component.apply {
                    this.addItemListener {
                        if (canOptionChange(this, currentOpenIssuesEnabled)) {
                            currentOpenIssuesEnabled = this.isSelected
                            settings.openIssuesEnabled = currentOpenIssuesEnabled
                            getSnykTaskQueueService(project)?.scan()
                        }
                    }
                    name = "Open issues"
                }
            }
        }
        row {
            cell {
                checkBox(
                    text = "Ignored issues",
                    getter = { settings.ignoredIssuesEnabled },
                    setter = { settings.ignoredIssuesEnabled = it }
                ).component.apply {
                    this.addItemListener {
                        if (canOptionChange(this, currentIgnoredIssuesEnabled)) {
                            currentIgnoredIssuesEnabled = this.isSelected
                            settings.ignoredIssuesEnabled =currentIgnoredIssuesEnabled
                            getSnykTaskQueueService(project)?.scan()
                        }
                    }
                    name = "Ignored issues"
                }
            }
        }
    }.apply {
        name = "issueViewPanel"
        border = JBUI.Borders.empty(2)
    }

    private fun canOptionChange(component: JBCheckBox, wasEnabled: Boolean): Boolean {
        val onlyOneEnabled = arrayOf(
            currentOpenIssuesEnabled,
            currentIgnoredIssuesEnabled,
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
