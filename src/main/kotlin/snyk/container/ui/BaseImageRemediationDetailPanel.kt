package snyk.container.ui

import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import snyk.container.BaseImageVulnerabilities
import snyk.container.ContainerIssuesForFile
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel

class BaseImageRemediationDetailPanel(
    private val imageIssues: ContainerIssuesForFile
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
            baseRemediationInfoPanel(),
            panelGridConstraints(0)
        )
    }

    private fun baseRemediationInfoPanel(): JPanel {
        val panel = JPanel(GridLayoutManager(4, 4, Insets(0, 0, 0, 0), 0, 0))

        panel.add(
            JLabel("Current image"),
            baseGridConstraints(0, 0)
        )
        val currentImage = imageIssues.baseImageRemediationInfo?.currentImage
        panel.add(
            JLabel(currentImage?.name),
            baseGridConstraints(0, 1, indent = 10)
        )
        var currentVulns = ""
        currentImage?.vulnerabilities.let {
            currentVulns = getVulnerabilitiesText(it!!)
        }
        panel.add(
            JLabel(currentVulns),
            baseGridConstraints(0, 2, indent = 20)
        )

        panel.add(
            JLabel("Minor upgrades"),
            baseGridConstraints(1, 0)
        )
        val minorUpgrades = imageIssues.baseImageRemediationInfo?.minorUpgrades
        panel.add(
            JLabel(minorUpgrades?.name),
            baseGridConstraints(1, 1, indent = 10)
        )
        var minorVulns = ""
        minorUpgrades?.vulnerabilities.let {
            minorVulns = getVulnerabilitiesText(it!!)
        }
        panel.add(
            JLabel(minorVulns),
            baseGridConstraints(1, 2, indent = 20)
        )

        panel.add(
            JLabel("Alternative upgrades"),
            baseGridConstraints(2, 0)
        )
        val alternativeUpgrades = imageIssues.baseImageRemediationInfo?.alternativeUpgrades
        panel.add(
            JLabel(alternativeUpgrades?.name),
            baseGridConstraints(2, 1, indent = 10)
        )
        var alternativeVulns = ""
        alternativeUpgrades?.vulnerabilities.let {
            alternativeVulns = getVulnerabilitiesText(it!!)
        }
        panel.add(
            JLabel(alternativeVulns),
            baseGridConstraints(2, 2, indent = 20)
        )

        return panel
    }

    private fun panelGridConstraints(row: Int) = baseGridConstraints(
        row = row,
        anchor = GridConstraints.ANCHOR_CENTER,
        fill = GridConstraints.FILL_BOTH,
        HSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        VSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        indent = 0
    )

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

    private fun getVulnerabilitiesText(vulnerabilities: BaseImageVulnerabilities): String {
        return "${vulnerabilities.critical} critical, " +
            "${vulnerabilities.high} high, " +
            "${vulnerabilities.medium} medium, " +
            "${vulnerabilities.low} low"
    }
}
