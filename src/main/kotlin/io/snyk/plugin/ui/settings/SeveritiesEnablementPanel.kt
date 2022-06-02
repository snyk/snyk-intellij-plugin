package io.snyk.plugin.ui.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import com.intellij.util.ui.JBUI
import io.snyk.plugin.Severity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper

class SeveritiesEnablementPanel() {
    private val settings
        get() = pluginSettings()

    private var currentCriticalSeverityEnabled = settings.criticalSeverityEnabled
    private var currentHighSeverityEnabled = settings.highSeverityEnabled
    private var currentMediumSeverityEnabled = settings.mediumSeverityEnabled
    private var currentLowSeverityEnabled = settings.lowSeverityEnabled

    val panel = panel {
        row {
            cell {
                checkBox(
                    Severity.CRITICAL.toPresentableString(),
                    { settings.criticalSeverityEnabled },
                    { settings.criticalSeverityEnabled = it }
                ).component.apply {
                    this.addItemListener {
                        isLastSeverityDisabling(this, currentCriticalSeverityEnabled)
                        currentCriticalSeverityEnabled = this.isSelected
                    }
                    name = Severity.CRITICAL.toPresentableString()
                }
            }
        }
        row {
            cell {
                checkBox(
                    Severity.HIGH.toPresentableString(),
                    { settings.highSeverityEnabled },
                    { settings.highSeverityEnabled = it }
                ).component.apply {
                    this.addItemListener {
                        isLastSeverityDisabling(this, currentHighSeverityEnabled)
                        currentHighSeverityEnabled = this.isSelected
                    }
                    name = Severity.HIGH.toPresentableString()
                }
            }
        }
        row {
            cell {
                checkBox(
                    Severity.MEDIUM.toPresentableString(),
                    { settings.mediumSeverityEnabled },
                    { settings.mediumSeverityEnabled = it }
                ).component.apply {
                    this.addItemListener {
                        isLastSeverityDisabling(this, currentMediumSeverityEnabled)
                        currentMediumSeverityEnabled = this.isSelected
                    }
                    name = Severity.MEDIUM.toPresentableString()
                }
            }
        }
        row {
            cell {
                checkBox(
                    Severity.LOW.toPresentableString(),
                    { settings.lowSeverityEnabled },
                    { settings.lowSeverityEnabled = it }
                ).component.apply {
                    this.addItemListener {
                        isLastSeverityDisabling(this, currentLowSeverityEnabled)
                        currentLowSeverityEnabled = this.isSelected
                    }
                    name = Severity.LOW.toPresentableString()
                }
            }
        }
    }.apply {
        name = "severityEnablementPanel"
        border = JBUI.Borders.empty(2)
    }

    private fun isLastSeverityDisabling(component: JBCheckBox, wasEnabled : Boolean): Boolean {
        val onlyOneEnabled = arrayOf(
            currentCriticalSeverityEnabled,
            currentHighSeverityEnabled,
            currentMediumSeverityEnabled,
            currentLowSeverityEnabled
        ).count { it } == 1

        if (onlyOneEnabled && wasEnabled) {
            component.isSelected = true
            SnykBalloonNotificationHelper.showWarnBalloonForComponent(
                "At least one Severity type should be selected",
                component
            )
        }
        return onlyOneEnabled
    }

}
