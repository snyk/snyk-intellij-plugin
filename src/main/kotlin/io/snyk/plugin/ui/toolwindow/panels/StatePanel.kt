package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.ui.components.ActionLink
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.ui.addSpacer
import io.snyk.plugin.ui.baseGridConstraints
import io.snyk.plugin.ui.getReadOnlyClickableHtmlJEditorPaneFixedSize
import java.awt.event.ActionEvent
import javax.swing.JPanel

class StatePanel(
  messageHtmlText: String,
  actionText: String? = null,
  action: ((e: ActionEvent) -> Unit)? = null,
) : JPanel() {
  init {
    layout = GridLayoutManager(4, 1, JBUI.insets(20, 100), -1, -1)

    add(
      getReadOnlyClickableHtmlJEditorPaneFixedSize(messageHtmlText),
      baseGridConstraints(row = 1, indent = 0),
    )

    if (actionText != null && action != null) {
      add(ActionLink(actionText, action), baseGridConstraints(row = 2, indent = 0))
    }

    addSpacer(row = 0)
    addSpacer(row = 3)
  }
}
