package io.snyk.plugin.ui.toolwindow

import com.intellij.ui.components.labels.LinkLabel
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPane
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel

class StatePanel(messageHtmlText: String, actionText: String? = null, action: (() -> Unit)? = null) : JPanel() {
    init {
        layout = GridLayoutManager(1, 1, Insets(20, 100, 20, 100), -1, -1)

        val innerPanel = JPanel()
        innerPanel.layout = GridLayoutManager(2, 2, Insets(0, 0, 0, 0), -1, -1)

        if (messageHtmlText.length > 100) {
            innerPanel.add(getReadOnlyClickableHtmlJEditorPane(messageHtmlText),
                baseGridConstraints(row = 0, fill = GridConstraints.FILL_HORIZONTAL)
            )
        } else {
            innerPanel.add(JLabel("<html>$messageHtmlText</html>"),
                baseGridConstraints(row = 0, indent = 0)
            )
        }

        if (actionText != null && action != null) {
            innerPanel.add(LinkLabel.create(actionText, action),
                baseGridConstraints(row = 1, indent = 0)
            )
        }

        add(innerPanel,
            baseGridConstraints(0, fill = GridConstraints.FILL_HORIZONTAL)
        )
    }
}
