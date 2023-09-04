package io.snyk.plugin

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import java.awt.Color
import javax.swing.Icon

enum class Severity {
    // order is important for comparator
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    override fun toString(): String = super.toString().lowercase()

    fun toPresentableString(): String =
        when (this) {
            CRITICAL -> "Critical Severity"
            HIGH -> "High Severity"
            MEDIUM -> "Medium Severity"
            LOW -> "Low Severity"
            else -> ""
        }

    fun getColor(): Color =
        when (this) {
            CRITICAL -> Color.decode("#9E261E")
            HIGH -> Color.decode("#9B3D15")
            MEDIUM -> Color.decode("#925C1E")
            LOW -> Color.decode("#585675")
            else -> UIUtil.getPanelBackground()
        }

    fun getBgColor(): Color =
        when (this) {
            CRITICAL -> Color.decode("#FFDAD8")
            HIGH -> Color.decode("#FFDBCC")
            MEDIUM -> Color.decode("#FFE8CD")
            LOW -> Color.decode("#EEEEEE")
            else -> UIUtil.getPanelBackground()
        }

    fun getHighlightSeverity(): HighlightSeverity =
        when (this) {
            CRITICAL -> HighlightSeverity.ERROR
            HIGH -> HighlightSeverity.ERROR
            MEDIUM -> HighlightSeverity.WARNING
            LOW -> HighlightSeverity.WEAK_WARNING
            else -> HighlightSeverity.WARNING
            // Don't use HighlightSeverity.INFORMATION as it's not visible in the Editor and in `Problems`
        }

    fun getQuickFixPriority(): PriorityAction.Priority =
        when (this) {
            // Don't use PriorityAction.Priority.TOP as it's used to show real QuickFix first (on top of the list)
            CRITICAL -> PriorityAction.Priority.HIGH
            HIGH -> PriorityAction.Priority.HIGH
            MEDIUM -> PriorityAction.Priority.NORMAL
            LOW -> PriorityAction.Priority.LOW
            else -> PriorityAction.Priority.LOW
        }

    fun getIcon(): Icon = SnykIcons.getSeverityIcon(this)

    companion object {
        private const val SEVERITY_CRITICAL = "critical"
        private const val SEVERITY_HIGH = "high"
        private const val SEVERITY_MEDIUM = "medium"
        private const val SEVERITY_LOW = "low"

        fun getFromIndex(index: Int): Severity =
            when (index) {
                4 -> CRITICAL
                3 -> HIGH
                2 -> MEDIUM
                1 -> LOW
                else -> UNKNOWN
            }

        fun getFromName(name: String): Severity =
            when (name) {
                SEVERITY_CRITICAL -> CRITICAL
                SEVERITY_HIGH -> HIGH
                SEVERITY_MEDIUM -> MEDIUM
                SEVERITY_LOW -> LOW
                else -> UNKNOWN
            }
    }
}
