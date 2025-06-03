package io.snyk.plugin.ui.toolwindow.panels

import io.snyk.plugin.ui.DescriptionHeaderPanel
import java.awt.BorderLayout
import javax.swing.JPanel

abstract class IssueDescriptionPanelBase(
) : JPanel(BorderLayout()), IssueDescriptionPanel {

    abstract fun secondRowTitlePanel(): DescriptionHeaderPanel

    abstract fun createMainBodyPanel(): Pair<JPanel, Int>

}

