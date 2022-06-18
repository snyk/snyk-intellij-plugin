package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.ui.components.labels.LinkLabel
import com.intellij.uiDesigner.core.GridLayoutManager
import io.snyk.plugin.ui.addSpacer
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPaneFixedSize
import java.awt.Insets
import javax.swing.JPanel

class StatePanel(messageHtmlText: String, actionText: String? = null, action: (() -> Unit)? = null) : JPanel() {
    init {
        layout = GridLayoutManager(4, 1, Insets(20, 100, 20, 100), -1, -1)

        add(
            getReadOnlyClickableHtmlJEditorPaneFixedSize(messageHtmlText),
            baseGridConstraints(row = 1, indent = 0)
        )

        if (actionText != null && action != null) {
            add(
                LinkLabel.create(actionText, action),
                baseGridConstraints(row = 2, indent = 0)
            )
        }

        addSpacer(row = 0)
        addSpacer(row = 3)
    }
}
