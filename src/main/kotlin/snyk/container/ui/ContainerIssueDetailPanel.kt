package snyk.container.ui

import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import icons.SnykIcons
import io.snyk.plugin.Severity
import snyk.container.ContainerIssue
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel

class ContainerIssueDetailPanel(
    private val issue: ContainerIssue
) : JPanel() {
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
            titlePanel(),
            panelGridConstraints(0)
        )
    }

    private fun titlePanel(): JPanel {
        val titlePanel = JPanel()
        titlePanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, 5)

        val titleLabel = JLabel().apply {
            font = io.snyk.plugin.ui.getFont(Font.BOLD, 20, font)
            text = " " + issue.title.ifBlank {
                when (issue.severity) {
                    Severity.CRITICAL -> "Critical Severity"
                    Severity.HIGH -> "High Severity"
                    Severity.MEDIUM -> "Medium Severity"
                    Severity.LOW -> "Low Severity"
                    else -> ""
                }
            }
            icon = SnykIcons.getSeverityIcon(issue.severity, SnykIcons.IconSize.SIZE24)
        }

        titlePanel.add(
            titleLabel,
            baseGridConstraints(0)
        )
        titlePanel.add(
            cwePanel(),
            baseGridConstraints(1, indent = 0)
        )

        return titlePanel
    }

    private fun cwePanel(): JPanel {
        val panel = JPanel(GridLayoutManager(1, 3, Insets(0, 0, 0, 0), 5, 0))

        panel.add(
            JLabel("Vulnerability"),
            baseGridConstraints(0)
        )

        return panel
    }

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
}
