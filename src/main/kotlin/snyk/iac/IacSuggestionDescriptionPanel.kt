package snyk.iac

import com.intellij.ide.BrowserUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import icons.SnykIcons
import io.snyk.plugin.Severity
import java.awt.*
import javax.swing.*
import javax.swing.event.HyperlinkEvent

class IacSuggestionDescriptionPanel(
    private val issue: IacIssue
) : JPanel() {

    private fun baseGridConstraints(
        row: Int,
        column: Int = 0,
        rowSpan: Int = 1,
        colSpan: Int = 1,
        anchor: Int = GridConstraints.ANCHOR_WEST,
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

    private fun panelGridConstraints(row: Int) = baseGridConstraints(
        row = row,
        anchor = GridConstraints.ANCHOR_CENTER,
        fill = GridConstraints.FILL_BOTH,
        HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        indent = 0
    )

    init {
        this.layout = GridLayoutManager(10, 1, Insets(20, 10, 20, 20), -1, 10)

        this.add(
            Spacer(),
            baseGridConstraints(
                9,
                anchor = GridConstraints.ANCHOR_CENTER,
                fill = GridConstraints.FILL_VERTICAL,
                HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
                VSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        this.add(
            getMainBodyPanel(),
            baseGridConstraints(1, indent = 0)
        )

        this.add(titlePanel(), panelGridConstraints(0))
    }

    private fun getMainBodyPanel(): JPanel {
        val panel = JPanel()

        panel.layout = GridLayoutManager(11, 2, Insets(20, 0, 20, 0), 50, -1)

        fun boldLabel(text: String) = JLabel(text).apply { font = io.snyk.plugin.ui.getFont(Font.BOLD, -1, JLabel().font) }

        panel.add(
            boldLabel("Issue:"),
            baseGridConstraints(2, 0)
        )
        panel.add(
            JLabel(issue.id),
            baseGridConstraints(2, 1)
        )

        panel.add(
            boldLabel("Impact:"),
            baseGridConstraints(3, 0)
        )
        panel.add(
            JLabel(issue.impact),
            baseGridConstraints(3, 1)
        )

        panel.add(
            boldLabel("Path:"),
            baseGridConstraints(4, 0)
        )
        panel.add(
            JLabel(issue.path.joinToString(" > ")),
            baseGridConstraints(4, 1)
        )

        return panel
    }

    private fun titlePanel(): JPanel {
        val titlePanel = JPanel()
        titlePanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, 5)

        val titleLabel = JLabel().apply {
            font = io.snyk.plugin.ui.getFont(Font.BOLD, 20, font)
            text = " " + if (issue.title.isNotBlank()) issue.title else when (issue.severity) {
                Severity.CRITICAL -> "Critical Severity"
                Severity.HIGH -> "High Severity"
                Severity.MEDIUM -> "Medium Severity"
                Severity.LOW -> "Low Severity"
                else -> ""
            }
            icon = SnykIcons.getSeverityIcon(issue.severity, SnykIcons.IconSize.SIZE24)
        }

        titlePanel.add(
            titleLabel,
            baseGridConstraints(0)
        )

        titlePanel.add(cwePanel(), baseGridConstraints(1, indent = 0))

        return titlePanel
    }

    private fun cwePanel(): Component {
        val panel = JPanel()

        panel.layout = GridLayoutManager(1, 3, Insets(0, 0, 0, 0), 5, 0)

        panel.add(
            JLabel("Policy Id"),
            baseGridConstraints(0)
        )

        panel.add(
            defaultFontLabel(" | ").apply {
                this.foreground = Color(this.foreground.red, this.foreground.green, this.foreground.blue, 50)
            },
            baseGridConstraints(0, column = 1, indent = 0)
        )

        val positionLabel = linkLabel(
            linkText = issue.id,
            toolTipText = "Click to open description in the Browser"
        ) {
            val url = issue.documentation
            BrowserUtil.open(url)
        }

        panel.add(positionLabel, baseGridConstraints(0, 2, indent = 0))

        return panel
    }

    private fun linkLabel(
        beforeLinkText: String = "",
        linkText: String,
        afterLinkText: String = "",
        toolTipText: String,
        customFont: Font? = null,
        onClick: (HyperlinkEvent) -> Unit): HyperlinkLabel {
        return HyperlinkLabel().apply {
            this.setHyperlinkText(beforeLinkText, linkText, afterLinkText)
            this.toolTipText = toolTipText
            this.font = io.snyk.plugin.ui.getFont(-1, 14, customFont ?: font)
            addHyperlinkListener {
                onClick.invoke(it)
            }
        }
    }

    private fun defaultFontLabel(labelText: String, bold: Boolean = false): JLabel {
        return JLabel().apply {
            val titleLabelFont: Font? = io.snyk.plugin.ui.getFont(if (bold) Font.BOLD else -1, 14, font)
            titleLabelFont?.let { font = it }
            text = labelText
        }
    }
}
