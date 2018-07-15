package io.snyk.plugin

import javax.swing.ImageIcon

object Icons {
    private def iconFromRes(name: String): ImageIcon = new ImageIcon(this.getClass.getResource(name))

    val calendar = iconFromRes("/icons/Calendar-icon.png")
    val time     = iconFromRes("/icons/Time-icon.png")
    val timezone = iconFromRes("/icons/Time-zone-icon.png")
}
