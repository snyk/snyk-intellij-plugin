package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.ui.ScrollPaneFactory
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.ui.buildBoldTitleLabel
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import snyk.common.SnykError

class SnykErrorPanel(snykError: SnykError) : JPanel() {

  init {
    this.layout = GridLayoutManager(11, 1, Insets(20, 0, 0, 0), -1, 10)

    this.add(
      Spacer(),
      GridConstraints(
        10,
        0,
        1,
        1,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_VERTICAL,
        1,
        GridConstraints.SIZEPOLICY_WANT_GROW,
        null,
        null,
        null,
        0,
        false,
      ),
    )

    val pathPanel = JPanel()
    pathPanel.layout = GridLayoutManager(4, 2, Insets(0, 0, 0, 0), -1, -1)
    this.add(
      pathPanel,
      GridConstraints(
        7,
        0,
        1,
        1,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        null,
        null,
        null,
        1,
        false,
      ),
    )

    pathPanel.add(
      buildBoldTitleLabel("Path"),
      GridConstraints(
        0,
        0,
        1,
        1,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false,
      ),
    )

    val pathTextArea = JTextArea(snykError.path)
    pathTextArea.lineWrap = true
    pathTextArea.wrapStyleWord = true
    pathTextArea.isOpaque = false
    pathTextArea.isEditable = false
    pathTextArea.background = UIUtil.getPanelBackground()
    pathTextArea.name = "pathTextArea"

    pathPanel.add(
      ScrollPaneFactory.createScrollPane(pathTextArea, true),
      GridConstraints(
        2,
        0,
        1,
        1,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
        GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
        null,
        null,
        null,
        1,
        false,
      ),
    )

    val messagePanel = JPanel()
    messagePanel.layout = GridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, -1)

    this.add(
      messagePanel,
      GridConstraints(
        8,
        0,
        1,
        1,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        null,
        null,
        null,
        1,
        false,
      ),
    )

    messagePanel.add(
      buildBoldTitleLabel("Message"),
      GridConstraints(
        0,
        0,
        1,
        1,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false,
      ),
    )

    val errorMessageTextArea = JTextArea(snykError.message)
    errorMessageTextArea.lineWrap = true
    errorMessageTextArea.wrapStyleWord = true
    errorMessageTextArea.isOpaque = false
    errorMessageTextArea.isEditable = false
    errorMessageTextArea.background = UIUtil.getPanelBackground()
    errorMessageTextArea.name = "errorMessageTextArea"

    messagePanel.add(
      ScrollPaneFactory.createScrollPane(errorMessageTextArea, true),
      GridConstraints(
        1,
        0,
        1,
        1,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
        GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
        null,
        null,
        null,
        1,
        false,
      ),
    )

    val errorLabelPanel = SeverityColorPanel(Severity.HIGH)
    errorLabelPanel.layout = GridLayoutManager(2, 2, Insets(10, 10, 10, 10), -1, -1)

    this.add(
      errorLabelPanel,
      GridConstraints(
        0,
        0,
        1,
        1,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        null,
        null,
        null,
        0,
        false,
      ),
    )

    val errorLabel = JLabel()

    val severityLabelFont: Font? = io.snyk.plugin.ui.getFont(-1, 14, errorLabel.font)

    if (severityLabelFont != null) {
      errorLabel.font = severityLabelFont
    }

    errorLabel.text = "Error"

    errorLabel.foreground = Color(-1)

    errorLabelPanel.add(
      errorLabel,
      GridConstraints(
        0,
        0,
        1,
        1,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false,
      ),
    )

    errorLabelPanel.add(
      Spacer(),
      GridConstraints(
        0,
        1,
        1,
        1,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW,
        1,
        null,
        null,
        null,
        0,
        false,
      ),
    )

    errorLabelPanel.add(
      Spacer(),
      GridConstraints(
        1,
        0,
        1,
        1,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_VERTICAL,
        1,
        GridConstraints.SIZEPOLICY_WANT_GROW,
        null,
        null,
        null,
        0,
        false,
      ),
    )
  }
}
