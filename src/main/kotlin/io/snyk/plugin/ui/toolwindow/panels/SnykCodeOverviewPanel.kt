package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.ui.panelGridConstraints
import snyk.common.lsp.IssueData
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel

class SnykCodeOverviewPanel(codeIssueData: IssueData) : JComponent() {
    init {
        this.layout = GridLayoutManager(2, 1, JBUI.emptyInsets(), -1, -1)
        val label = JLabel("<html>" + codeIssueData.message + "</html>").apply {
            this.isOpaque = false
            this.background = UIUtil.getPanelBackground()
            this.font = io.snyk.plugin.ui.getFont(-1, 14, this.font)
            this.preferredSize = Dimension() // this is the key part for shrink/grow.
        }
        this.add(label, panelGridConstraints(1, indent = 1))

    }
}
