package io.snyk.plugin.ui

import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.isSnykCodeAvailable
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

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

fun buildTextAreaWithLabelPanel(title: String, text: String): JPanel {
    val titleLabel = JLabel()
    val vulnerableModuleLabelFont: Font? = getFont(Font.BOLD, -1, titleLabel.font)

    if (vulnerableModuleLabelFont != null) {
        titleLabel.font = vulnerableModuleLabelFont
    }

    titleLabel.text = title

    val wrapPanel = JPanel(BorderLayout())

    wrapPanel.add(titleLabel, BorderLayout.WEST)

    val textArea = JTextArea(text)
    textArea.lineWrap = true
    textArea.wrapStyleWord = true
    textArea.isOpaque = false
    textArea.isEditable = false
    textArea.background = UIUtil.getPanelBackground()

    wrapPanel.add(ScrollPaneFactory.createScrollPane(textArea, true), BorderLayout.CENTER)

    return wrapPanel
}

fun snykCodeAvailabilityPostfix(): String = when {
    !isSnykCodeAvailable(getApplicationSettingsStateService().customEndpointUrl) -> " (disabled for endpoint)"
    !(getApplicationSettingsStateService().sastOnServerEnabled ?: false) -> " (disabled for organization)"
    else -> ""
}
