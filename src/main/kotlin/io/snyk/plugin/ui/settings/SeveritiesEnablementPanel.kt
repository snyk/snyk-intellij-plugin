package io.snyk.plugin.ui.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.bindSelected
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
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                // we need to change the settings in here in order for the validation to work pre-apply
                settings.criticalSeverityEnabled = hasBeenSelected
                isLastSeverityDisabling(it, !hasBeenSelected)
            }
            // bindSelected is needed to trigger apply() on the settings dialog that this panel is rendered in
            // that way we trigger the re-rendering of the Tree Nodes
            .bindSelected(settings::criticalSeverityEnabled)
        }
        row {
            checkBox(Severity.HIGH.toPresentableString()).applyToComponent {
                name = text
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                settings.highSeverityEnabled = hasBeenSelected
                isLastSeverityDisabling(it, !hasBeenSelected)
            }
            .bindSelected(settings::highSeverityEnabled)
        }
        row {
            checkBox(Severity.MEDIUM.toPresentableString()).applyToComponent {
                name = text
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                settings.mediumSeverityEnabled = hasBeenSelected
                isLastSeverityDisabling(it, !hasBeenSelected)
            }
            .bindSelected(settings::mediumSeverityEnabled)
        }
        row {
            checkBox(Severity.LOW.toPresentableString()).applyToComponent {
                name = text
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                settings.lowSeverityEnabled = hasBeenSelected
                isLastSeverityDisabling(it, !hasBeenSelected)
            }
            .bindSelected(settings::lowSeverityEnabled)
        }
    }.apply {
        name = "severityEnablementPanel"
        border = JBUI.Borders.empty(2)
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
