package io.snyk.plugin

import javax.swing.ImageIcon

class Icons {
    companion object {
        fun iconFromRes(name: String): ImageIcon = ImageIcon(Icons::class.java.getResource(name))

        val calendar = iconFromRes("/icons/Calendar-icon.png")
        val time     = iconFromRes("/icons/Time-icon.png")
        val timezone = iconFromRes("/icons/Time-zone-icon.png")
    }
}