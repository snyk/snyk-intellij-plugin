package io.snyk.plugin.ui

import java.awt.Font
import javax.swing.ImageIcon
import javax.swing.JLabel

fun boldLabel(title: String): JLabel {
    val label = JLabel(title)
    val labelFont = label.font
    label.font = labelFont.deriveFont(labelFont.style or Font.BOLD)

    return label
}

fun iconLabel(imageIcon: ImageIcon): JLabel {
    val label = JLabel()
    label.horizontalAlignment = 0
    label.icon = imageIcon
    label.text = ""

    return label
}
