package io.snyk.plugin.ui.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper

class IssueViewOptionsPanel {
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
                        isOptionNotSelected(this, currentOpenIssuesEnabled)
                        currentOpenIssuesEnabled = this.isSelected
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
                        isOptionNotSelected(this, currentIgnoredIssuesEnabled)
                        currentIgnoredIssuesEnabled = this.isSelected
                    }
                    name = "Ignored issues"
                }
            }
        }
    }.apply {
        name = "issueViewPanel"
        border = JBUI.Borders.empty(2)
    }

    private fun isOptionNotSelected(component: JBCheckBox, wasEnabled: Boolean): Boolean {
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
        }
        return onlyOneEnabled
    }
}
