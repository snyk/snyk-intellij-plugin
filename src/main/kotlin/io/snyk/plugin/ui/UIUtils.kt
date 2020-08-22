package io.snyk.plugin.ui

import java.awt.Font
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel

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

fun getFont(style: Int, size: Int, currentFont: Font?): Font? {
    if (currentFont == null) {
        return null
    }

    return Font(currentFont.name, if (style >= 0) style else currentFont.style, if (size >= 0) size else currentFont.size)
}

fun buildBoldTitleLabel(title: String): JLabel {
    val bold16pxLabel = JLabel(title)
    val detailedPathsAndRemediationLabelFont: Font? = getFont(Font.BOLD, 16, bold16pxLabel.font)

    if (detailedPathsAndRemediationLabelFont != null) {
        bold16pxLabel.font = detailedPathsAndRemediationLabelFont
    }

    return bold16pxLabel
}

fun buildTwoLabelsPanel(title: String, text: String): JPanel {
    val titleLabel = JLabel()
    val vulnerableModuleLabelFont: Font? = getFont(Font.BOLD, -1, titleLabel.font)

    if (vulnerableModuleLabelFont != null) {
        titleLabel.font = vulnerableModuleLabelFont
    }

    titleLabel.text = title

    val wrapPanel = JPanel()

    wrapPanel.add(titleLabel)
    wrapPanel.add(JLabel(text))

    return wrapPanel
}
