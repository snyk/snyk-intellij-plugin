package io.snyk.plugin.ui.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import io.snyk.plugin.Severity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.awt.event.ItemEvent

class SeveritiesEnablementPanel {
    private val settings
        get() = pluginSettings()

    private var currentCriticalSeverityEnabled = settings.criticalSeverityEnabled
    private var currentHighSeverityEnabled = settings.highSeverityEnabled
    private var currentMediumSeverityEnabled = settings.mediumSeverityEnabled
    private var currentLowSeverityEnabled = settings.lowSeverityEnabled

    val panel = panel {
        row {
            checkBox(Severity.CRITICAL.toPresentableString()).applyToComponent {
                name = text
                isSelected = settings.criticalSeverityEnabled
                this.addItemListener {
                    correctLastSeverityDisabled(it)
                    settings.criticalSeverityEnabled = this.isSelected
                }
            }
        }
        row {
            checkBox(Severity.HIGH.toPresentableString()).applyToComponent {
                name = text
                isSelected = settings.highSeverityEnabled
                this.addItemListener {
                    correctLastSeverityDisabled(it)
                    settings.highSeverityEnabled = this.isSelected
                }
            }
        }
        row {
            checkBox(Severity.MEDIUM.toPresentableString()).applyToComponent {
                name = text
                isSelected = settings.mediumSeverityEnabled
                this.addItemListener {
                    correctLastSeverityDisabled(it)
                    settings.mediumSeverityEnabled = this.isSelected
                }
            }
        }
        row {
            checkBox(Severity.LOW.toPresentableString()).applyToComponent {
                name = text
                isSelected = settings.lowSeverityEnabled
                this.addItemListener {
                    correctLastSeverityDisabled(it)
                    settings.lowSeverityEnabled = this.isSelected
                }
            }
        }
    }.apply {
        name = "severityEnablementPanel"
        border = JBUI.Borders.empty(2)
    }

    private fun JBCheckBox.correctLastSeverityDisabled(it: ItemEvent) {
        val deselected = it.stateChange == ItemEvent.DESELECTED
        isLastSeverityDisabling(this, deselected)
    }

    private fun isLastSeverityDisabling(component: JBCheckBox, wasEnabled: Boolean): Boolean {
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
