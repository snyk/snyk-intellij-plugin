package io.snyk.plugin.ui

import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ColorUtil
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.isSnykCodeAvailable
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.LabelProvider
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.text.html.HTMLDocument

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

    return Font(
        currentFont.name,
        if (style >= 0) style else currentFont.style,
        if (size >= 0) size else currentFont.size
    )
}

fun buildBoldTitleLabel(title: String): JLabel {
    val bold16pxLabel = JLabel(title)
    val detailedPathsAndRemediationLabelFont: Font? = getFont(Font.BOLD, 16, bold16pxLabel.font)

    if (detailedPathsAndRemediationLabelFont != null) {
        bold16pxLabel.font = detailedPathsAndRemediationLabelFont
    }

    return bold16pxLabel
}

fun insertTitleAndResizableTextIntoPanelColumns(
    panel: JPanel,
    row: Int,
    title: String,
    htmlText: String,
    textFont: Font = UIUtil.getLabelFont()
) {
    panel.add(
        boldLabel(title),
        baseGridConstraints(row, 0, anchor = GridConstraints.ANCHOR_NORTHWEST)
    )
    panel.add(
        getReadOnlyClickableHtmlJEditorPane(htmlText, textFont, noBorder = true),
        panelGridConstraints(row, 1)
    )
}

fun snykCodeAvailabilityPostfix(): String = when {
    !isSnykCodeAvailable(pluginSettings().customEndpointUrl) -> " (disabled for endpoint)"
    !(pluginSettings().sastOnServerEnabled ?: false) -> " (disabled for organization)"
    else -> ""
}

fun getReadOnlyClickableHtmlJEditorPane(
    htmlText: String,
    font: Font = UIUtil.getLabelFont(),
    noBorder: Boolean = false
): JEditorPane {
    // don't remove that!
    // Some magic (side-effect? customStyleSheet?) happens when JBHtmlEditorKit() initializing
    // that make html tags like <em>, <p>, <ul> etc. be treated properly inside JEditorPane
    JBHtmlEditorKit()

    return JEditorPane(
        "text/html",
        "<html>$htmlText</html>"
    ).apply {
        isEditable = false
        background = UIUtil.getPanelBackground()
        preferredSize = Dimension() // this is the key part for shrink/grow.

        // add a CSS rule to force body tags to use the default label font
        // instead of the value in javax.swing.text.html.default.css
        val fontColor = UIUtil.getTextFieldForeground()
        val bodyRule = UIUtil.displayPropertiesToCSS(font, fontColor) +
            "a { color: #${ColorUtil.toHex(JBUI.CurrentTheme.Link.linkColor())}; }"
        (document as HTMLDocument).styleSheet.addRule(bodyRule)

        // open clicked link in browser
        addHyperlinkListener {
            BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(it)
        }
        if (noBorder) border = null
    }
}

fun getStandardLayout(rowCount: Int = 2, columnCount: Int = 2) =
    GridLayoutManager(rowCount, columnCount, Insets(5, 5, 5, 5), -1, -1)

fun getPanelWithColumns(rowCount: Int, columnCount: Int): JPanel =
    JPanel().apply { layout = getStandardLayout(rowCount, columnCount) }

fun getPanelWithColumns(name: String, rowCount: Int, columnCount: Int): JPanel =
    getPanelWithColumns(rowCount, columnCount).apply { this.name = name }

fun addAndGetCenteredPanel(container: Container, rowCount: Int, columnCount: Int): JPanel {
    val holder = getPanelWithColumns(3, 3)
    container.add(holder, baseGridConstraints(0, 0))
    val panel = getPanelWithColumns("actualAuthPanel", rowCount = rowCount, columnCount = columnCount)
    holder.add(panel, baseGridConstraints(1, 1))
    return panel
}

fun baseGridConstraints(
    row: Int,
    column: Int = 0,
    rowSpan: Int = 1,
    colSpan: Int = 1,
    anchor: Int = GridConstraints.ANCHOR_CENTER,
    fill: Int = GridConstraints.FILL_NONE,
    HSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
    VSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
    minimumSize: Dimension? = null,
    preferredSize: Dimension? = null,
    maximumSize: Dimension? = null,
    indent: Int = 1,
    useParentLayout: Boolean = false
): GridConstraints {
    return GridConstraints(
        row, column, rowSpan, colSpan, anchor, fill, HSizePolicy, VSizePolicy, minimumSize, preferredSize,
        maximumSize, indent, useParentLayout
    )
}

fun baseGridConstraintsAnchorWest(
    row: Int,
    column: Int = 0,
    fill: Int = GridConstraints.FILL_NONE,
    HSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
    VSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
    indent: Int = 1
): GridConstraints = baseGridConstraints(
    row = row,
    column = column,
    anchor = GridConstraints.ANCHOR_WEST,
    fill = fill,
    HSizePolicy = HSizePolicy,
    VSizePolicy = VSizePolicy,
    indent = indent
)

fun panelGridConstraints(
    row: Int,
    column: Int = 0
) = baseGridConstraints(
    row = row,
    column = column,
    fill = GridConstraints.FILL_BOTH,
    HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
    VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
    indent = 0
)

fun descriptionHeaderPanel(
    issueNaming: String,
    cwes: List<String> = emptyList(),
    cves: List<String> = emptyList(),
    cvssScore: String? = null,
    cvsSv3: String? = null,
    id: String,
    idUrl: String? = null
): JPanel {
    val panel = JPanel()

    val columnCount = 1 + // name label
        cwes.size * 2 + // CWEs with `|`
        cves.size * 2 + // CVEs with `|`
        2 + // CVSS
        2 // Snyk description
    panel.layout = GridLayoutManager(1, columnCount, Insets(0, 0, 0, 0), 5, 0)

    panel.add(
        JLabel(issueNaming),
        baseGridConstraintsAnchorWest(0)
    )

    val labelProvider = LabelProvider()

    var lastColumn =
        addRowOfItemsToPanel(panel, 0, cwes.map { cwe -> labelProvider.getCWELabel(cwe) })

    lastColumn =
        addRowOfItemsToPanel(panel, lastColumn, cves.map { cve -> labelProvider.getCVELabel(cve) })

    if (cvssScore != null && cvsSv3 != null) {
        val label = listOf(labelProvider.getCVSSLabel("CVSS $cvssScore", cvsSv3))
        lastColumn = addRowOfItemsToPanel(panel, lastColumn, label)
    }

    val label = listOf(labelProvider.getVulnerabilityLabel(id.toUpperCase(), idUrl))
    addRowOfItemsToPanel(panel, lastColumn, label)

    return panel
}

fun addRowOfItemsToPanel(
    panel: JPanel,
    startingColumn: Int,
    items: List<JLabel>,
    separator: String = " | ",
    firstSeparator: Boolean = true,
    opaqueSeparator: Boolean = true
): Int {
    var currentColumn = startingColumn
    items.forEach { item ->
        if (currentColumn != startingColumn || firstSeparator) {
            currentColumn++
            panel.add(
                JLabel(separator).apply { if (opaqueSeparator) makeOpaque(this, 50) },
                baseGridConstraints(0, column = currentColumn, indent = 0)
            )
        }
        currentColumn++
        panel.add(item, baseGridConstraints(0, column = currentColumn, indent = 0))
    }
    return currentColumn
}

private fun makeOpaque(component: JComponent, alpha: Int) {
    component.foreground = Color(
        component.foreground.red,
        component.foreground.green,
        component.foreground.blue,
        alpha
    )
}
