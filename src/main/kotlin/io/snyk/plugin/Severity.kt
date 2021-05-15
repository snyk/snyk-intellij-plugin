package io.snyk.plugin

import com.intellij.util.ui.UIUtil
import java.awt.Color

class Severity {
    companion object {
        const val CRITICAL = "critical"
        const val HIGH = "high"
        const val MEDIUM = "medium"
        const val LOW = "low"

        fun getIndex(severity: String): Int =
            when (severity) {
                CRITICAL -> 4
                HIGH -> 3
                MEDIUM -> 2
                LOW -> 1
                else -> 0
            }

        fun toName(index: Int): String =
            when (index) {
                4 -> CRITICAL
                3 -> HIGH
                2 -> MEDIUM
                1 -> LOW
                else -> LOW
            }

        fun getColor(severity: String): Color =
            when (severity) {
                CRITICAL -> Color.decode("#AD1A1A")
                HIGH -> Color.decode("#C75450")
                MEDIUM -> Color.decode("#EDA200")
                LOW -> Color.decode("#6E6E6E")
                else -> UIUtil.getPanelBackground()
            }
    }
}
