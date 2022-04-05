package io.snyk.plugin.ui.toolwindow

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.wrapWithScrollPane
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

private val EMPTY_PANEL = JBUI.Panels.simplePanel()

abstract class IssueDescriptionPanelBase(
    private val title: String,
    private val severity: String
) : JPanel(BorderLayout()), IssueDescriptionPanel {

    /**
     * **MUST** be invoked in derived class to actually create the UI elements.
     * Can't be part of constructor due to `state` usage in underling abstract/open methods/props:
     */
    protected fun createUI() {
        this.add(
            wrapWithScrollPane(descriptionBodyPanel()),
            BorderLayout.CENTER
        )
        if (isBottomPanelNeeded) {
            this.add(
                bottomPanel(),
                BorderLayout.SOUTH
            )
        }
    }

    private fun descriptionBodyPanel(): JPanel = JPanel(BorderLayout()).apply {
        add(titlePanel(), BorderLayout.NORTH)
        val (mainBodyPanel, rowForSpacer) = createMainBodyPanel()
        mainBodyPanel.add(
            Spacer(),
            baseGridConstraints(
                row = rowForSpacer,
                fill = GridConstraints.FILL_VERTICAL,
                hSizePolicy = GridConstraints.SIZEPOLICY_CAN_SHRINK,
                vSizePolicy = GridConstraints.SIZEPOLICY_WANT_GROW
            )
        )
        add(mainBodyPanel, BorderLayout.CENTER)
    }

    fun titlePanel(): JPanel {
        val titlePanel = JPanel()
        titlePanel.layout = GridLayoutManager(2, 2, Insets(20, 10, 20, 20), -1, 5)
        val titleLabel = JLabel().apply {
            font = io.snyk.plugin.ui.getFont(Font.BOLD, 20, font)
            text = getTitleText()
            icon = getTitleIcon()
        }

        titlePanel.add(
            titleLabel,
            baseGridConstraintsAnchorWest(0)
        )

        titlePanel.add(
            secondRowTitlePanel(),
            baseGridConstraintsAnchorWest(row = 1, column = 0, indent = 0)
        )

        return titlePanel
    }

    protected open fun getTitleIcon() = SnykIcons.getSeverityIcon(severity, SnykIcons.IconSize.SIZE24)

    private fun getTitleText() = " " + title.ifBlank {
        when (severity) {
            Severity.CRITICAL -> "Critical Severity"
            Severity.HIGH -> "High Severity"
            Severity.MEDIUM -> "Medium Severity"
            Severity.LOW -> "Low Severity"
            else -> ""
        }
    }

    abstract fun secondRowTitlePanel(): JPanel

    abstract fun createMainBodyPanel(): Pair<JPanel, Int>

    private fun bottomPanel(): JPanel = JBUI.Panels.simplePanel().apply {
        addToLeft(extraBottomLeftPanel)
        addToRight(bottomRightButtonsPanel())
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }

    protected open val extraBottomLeftPanel: JPanel = EMPTY_PANEL

    private fun bottomRightButtonsPanel(): JPanel = JPanel(
        GridLayoutManager(1, 2, JBUI.emptyInsets(), -1, -1)
    ).apply {
        border = Borders.empty(7, 10)
        bottomRightButtons.forEachIndexed { index, button ->
            add(button, baseGridConstraints(row = 0, column = index))
        }
    }

    protected open val bottomRightButtons: List<JButton> = emptyList()

    private val isBottomPanelNeeded: Boolean
        get() = extraBottomLeftPanel != EMPTY_PANEL || bottomRightButtons.isNotEmpty()
}

