package io.snyk.plugin.ui.toolwindow.panels

import io.snyk.plugin.Severity
import java.awt.Graphics
import javax.swing.JPanel

class SeverityColorPanel(private val severity: Severity) : JPanel() {
  override fun paintComponent(graphics: Graphics) {
    super.paintComponent(graphics)

    graphics.color = severity.getColor()

    graphics.fillRoundRect(0, 0, 150, this.height, 5, 5)
    graphics.fillRect(0, 0, 20, this.height)
  }
}
