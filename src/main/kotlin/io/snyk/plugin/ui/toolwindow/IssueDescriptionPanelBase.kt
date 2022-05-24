package io.snyk.plugin.ui.toolwindow

import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.addSpacer
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
        mainBodyPanel.addSpacer(rowForSpacer)
        add(mainBodyPanel, BorderLayout.CENTER)
    }

    fun titlePanel(insets: Insets = Insets(20, 10, 20, 20), indent: Int = 1): JPanel {
        val titlePanel = JPanel()
        titlePanel.layout = GridLayoutManager(2, 2, insets, -1, 5)
        val titleLabel = JLabel().apply {
            font = io.snyk.plugin.ui.getFont(Font.BOLD, 20, font)
            text = getTitleText()
            icon = getTitleIcon()
        }

        titlePanel.add(
            titleLabel,
            baseGridConstraintsAnchorWest(row = 0, indent = indent)
        )

        titlePanel.add(
            secondRowTitlePanel(),
            baseGridConstraintsAnchorWest(row = 1, column = 0, indent = indent)
        )

        return titlePanel
    }

    protected open fun getTitleIcon() = SnykIcons.getSeverityIcon(severity, SnykIcons.IconSize.SIZE32)

    private fun getTitleText() = " " + title.ifBlank {
        when (severity) {
            Severity.CRITICAL -> "Critical Severity"
            Severity.HIGH -> "High Severity"
            Severity.MEDIUM -> "Medium Severity"
            Severity.LOW -> "Low Severity"
            else -> ""
        }
    }

    abstract fun secondRowTitlePanel(): DescriptionHeaderPanel

    abstract fun createMainBodyPanel(): Pair<JPanel, Int>

    private fun bottomPanel(): JPanel = JBUI.Panels.simplePanel().apply {
        addToLeft(getExtraBottomLeftPanel())
        addToRight(bottomRightButtonsPanel())
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
    }

    protected open fun getExtraBottomLeftPanel(): JPanel = EMPTY_PANEL

    private fun bottomRightButtonsPanel(): JPanel = JPanel(
        GridLayoutManager(1, 2, JBUI.emptyInsets(), -1, -1)
    ).apply {
        border = Borders.empty(7, 10)
        getBottomRightButtons().forEachIndexed { index, button ->
            add(button, baseGridConstraints(row = 0, column = index))
        }
    }

    protected open fun getBottomRightButtons(): List<JButton> = emptyList()

    private val isBottomPanelNeeded: Boolean
        get() = getExtraBottomLeftPanel() != EMPTY_PANEL || getBottomRightButtons().isNotEmpty()
}

