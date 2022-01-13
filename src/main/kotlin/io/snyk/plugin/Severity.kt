package io.snyk.plugin

import com.intellij.util.ui.UIUtil
import java.awt.Color

class Severity {
    companion object {
        const val CRITICAL = "critical"
        const val HIGH = "high"
        const val MEDIUM = "medium"
        const val LOW = "low"
        const val UNKNOWN = "unknown"

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
                else -> UNKNOWN
            }

        fun getColor(severity: String): Color =
            when (severity) {
                CRITICAL -> Color.decode("#9E261E")
                HIGH -> Color.decode("#9B3D15")
                MEDIUM -> Color.decode("#925C1E")
                LOW -> Color.decode("#585675")
                else -> UIUtil.getPanelBackground()
            }

        fun getBgColor(severity: String): Color =
            when (severity) {
                CRITICAL -> Color.decode("#FFDAD8")
                HIGH -> Color.decode("#FFDBCC")
                MEDIUM -> Color.decode("#FFE8CD")
                LOW -> Color.decode("#EEEEEE")
                else -> UIUtil.getPanelBackground()
            }
    }
}
