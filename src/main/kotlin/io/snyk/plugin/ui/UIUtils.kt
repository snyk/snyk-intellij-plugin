package io.snyk.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.ActionLink
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.Alarm
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.LabelProvider
import org.apache.commons.text.StringEscapeUtils
import snyk.common.isSnykCodeAvailable
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.text.html.HTMLDocument
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

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

fun snykCodeAvailabilityPostfix(): String {
    val sastOnServerEnabled = pluginSettings().sastOnServerEnabled
    val sastSettingsError = pluginSettings().sastSettingsError
    return when {
        sastSettingsError == true -> " (Snyk Code settings misconfigured)"
        sastOnServerEnabled == false -> " (disabled in Snyk.io)"
        !isSnykCodeAvailable(pluginSettings().customEndpointUrl) -> " (disabled for endpoint)"
        sastOnServerEnabled == null -> " (unreachable server settings)"
        else -> ""
    }
}

/** Be careful! On macOS it's height could be set to 0 in some cases:
 * would blame `fill = FILL_HORIZONTAL`, but sometimes even with pure `panelGridConstraints` it's not shown (height=0)
 * another suspect could be calling that fun inside `init{}` of the Panel class.*/
fun getReadOnlyClickableHtmlJEditorPane(
    htmlText: String,
    font: Font = UIUtil.getLabelFont(),
    noBorder: Boolean = false
): JEditorPane = getReadOnlyClickableHtmlJEditorPaneFixedSize(htmlText, font, noBorder).apply {
    preferredSize = Dimension() // this is the key part for shrink/grow.
}

fun getReadOnlyClickableHtmlJEditorPaneFixedSize(
    htmlText: String,
    font: Font = UIUtil.getLabelFont(),
    noBorder: Boolean = false
): JEditorPane {
    // don't remove that!
    // Some magic (side-effect? customStyleSheet?) happens when JBHtmlEditorKit() initializing
    // that make html tags like <em>, <p>, <ul> etc. be treated properly inside JEditorPane
    HTMLEditorKitBuilder.simple()

    return JEditorPane(
        "text/html",
        "<html>$htmlText</html>"
    ).apply {
        isEditable = false
        background = UIUtil.getPanelBackground()

        // add a CSS rule to force body tags to use the default label font
        // instead of the value in javax.swing.text.html.default.css
        val fontColor = UIUtil.getTextFieldForeground()
        val bodyRule = UIUtil.displayPropertiesToCSS(font, fontColor) +
            "a { color: #${ColorUtil.toHex(JBUI.CurrentTheme.Link.Foreground.ENABLED)}; }"
        (document as HTMLDocument).styleSheet.addRule(bodyRule)

        (document as HTMLDocument).styleSheet.addRule(
            "h1, h2, h3, h4 { font-size: 1.1em; margin-bottom: 0em; }"
        )

        // open clicked link in browser
        addHyperlinkListener {
            BrowserHyperlinkListener.INSTANCE.hyperlinkUpdate(it)
        }
        if (noBorder) border = null
        name = htmlText
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
    hSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
    vSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
    minimumSize: Dimension? = null,
    preferredSize: Dimension? = null,
    maximumSize: Dimension? = null,
    indent: Int = 1,
    useParentLayout: Boolean = false
): GridConstraints {
    return GridConstraints(
        row, column, rowSpan, colSpan, anchor, fill, hSizePolicy, vSizePolicy, minimumSize, preferredSize,
        maximumSize, indent, useParentLayout
    )
}

fun baseGridConstraintsAnchorWest(
    row: Int,
    column: Int = 0,
    fill: Int = GridConstraints.FILL_NONE,
    hSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
    vSizePolicy: Int = GridConstraints.SIZEPOLICY_FIXED,
    indent: Int = 1
): GridConstraints = baseGridConstraints(
    row = row,
    column = column,
    anchor = GridConstraints.ANCHOR_WEST,
    fill = fill,
    hSizePolicy = hSizePolicy,
    vSizePolicy = vSizePolicy,
    indent = indent
)

fun panelGridConstraints(
    row: Int,
    column: Int = 0,
    fill: Int = GridConstraints.FILL_BOTH,
    indent: Int = 0
) = baseGridConstraints(
    row = row,
    column = column,
    fill = fill,
    hSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
    vSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
    indent = indent
)

fun JPanel.addSpacer(row: Int) =
    this.add(
        Spacer(),
        baseGridConstraints(
            row = row,
            fill = GridConstraints.FILL_VERTICAL,
            hSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
            vSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW
        )
    )

fun descriptionHeaderPanel(
    issueNaming: String,
    cwes: List<String> = emptyList(),
    cves: List<String> = emptyList(),
    cvssScore: String? = null,
    cvssV3: String? = null,
    id: String? = null,
    idUrl: String? = null,
    customLabels: List<ActionLink> = emptyList()
): DescriptionHeaderPanel {
    val panel = DescriptionHeaderPanel()
    val font14 = getFont(-1, 14, panel.font)

    val columnCount = 1 + // name label
        cwes.size * 2 + // CWEs with `|`
        cves.size * 2 + // CVEs with `|`
        2 + // CVSS
        2 + // Snyk description
        customLabels.size * 2 // Labels with `|`
    panel.layout = GridLayoutManager(1, columnCount, Insets(0, 0, 0, 0), 5, 0)

    panel.add(
        JLabel(issueNaming).apply { font = font14 },
        baseGridConstraintsAnchorWest(row = 0, indent = 0)
    )

    val labelProvider = LabelProvider()

    var lastColumn =
        addRowOfItemsToPanel(
            panel = panel,
            startingColumn = 0,
            items = cwes.map { cwe -> labelProvider.getCWELabel(cwe) },
            customFont = font14
        )

    lastColumn =
        addRowOfItemsToPanel(
            panel = panel,
            startingColumn = lastColumn,
            items = cves.map { cve -> labelProvider.getCVELabel(cve) },
            customFont = font14
        )

    if (cvssScore != null && cvssV3 != null) {
        val label = listOf(labelProvider.getCVSSLabel("CVSS $cvssScore", cvssV3))
        lastColumn = addRowOfItemsToPanel(panel, lastColumn, label, customFont = font14)
    }

    if (id != null) {
        val label = listOf(labelProvider.getVulnerabilityLabel(id, idUrl))
        lastColumn = addRowOfItemsToPanel(panel, lastColumn, label, customFont = font14)
    }
    addRowOfItemsToPanel(panel, lastColumn, customLabels, customFont = font14)

    return panel
}

class DescriptionHeaderPanel : JPanel()

fun addRowOfItemsToPanel(
    panel: JPanel,
    startingColumn: Int,
    items: List<ActionLink>,
    separator: String = " | ",
    firstSeparator: Boolean = true,
    opaqueSeparator: Boolean = true,
    customFont: Font? = null
): Int {
    var currentColumn = startingColumn
    items.forEach { item ->
        if (currentColumn != startingColumn || firstSeparator) {
            currentColumn++
            panel.add(
                JLabel(separator).apply {
                    if (opaqueSeparator) makeOpaque(this, 50)
                    if (customFont != null) this.font = customFont
                },
                baseGridConstraints(0, column = currentColumn, indent = 0)
            )
        }
        if (customFont != null) item.font = customFont
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

private val scrollPaneAlarm = Alarm()

fun wrapWithScrollPane(panel: JPanel): JScrollPane {
    val scrollPane = ScrollPaneFactory.createScrollPane(
        panel,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    )
    // hack to scroll Panel to beginning after all it content (hopefully) loaded
    scrollPaneAlarm.addRequest(
        {
            ApplicationManager.getApplication().invokeLater {
                if (!scrollPane.isShowing) return@invokeLater
                scrollPane.verticalScrollBar.value = 0
                scrollPane.horizontalScrollBar.value = 0
            }
        }, 100
    )
    return scrollPane
}

fun txtToHtml(s: String): String {
    val escapedHtml = StringEscapeUtils.escapeHtml4(s)
    val newLineConverted = escapedHtml
        .replace("\n", "<br>")
        .replace("\t", "&nbsp; &nbsp; &nbsp;")
    // html link converter "stolen" from https://stackoverflow.com/a/12053940/7577274
    val str =
        "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:\'\".,<>?«»“”‘’]))"
    val patt: Pattern = Pattern.compile(str)
    val matcher: Matcher = patt.matcher(newLineConverted)
    return matcher.replaceAll("<a href=\"$1\">$1</a>")
}

fun getDisabledIcon(originalIcon: Icon?): Icon? = originalIcon?.let { IconLoader.getDisabledIcon(it) }

fun expandTreeNodeRecursively(tree: JTree, node: DefaultMutableTreeNode) {
    tree.expandPath(TreePath(node.path))
    node.children().asSequence().forEach {
        expandTreeNodeRecursively(tree, it as DefaultMutableTreeNode)
    }
}
