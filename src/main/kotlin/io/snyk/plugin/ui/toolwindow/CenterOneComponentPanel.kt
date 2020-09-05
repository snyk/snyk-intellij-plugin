package io.snyk.plugin.ui.toolwindow

import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CenterOneComponentPanel(component: JComponent) : JPanel() {
    init {
        layout = GridBagLayout()

        add(component)
    }
}
