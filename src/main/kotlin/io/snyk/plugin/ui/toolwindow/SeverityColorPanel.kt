package io.snyk.plugin.ui.toolwindow

import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Graphics
import javax.swing.JPanel

class SeverityColorPanel(private val severity: String) : JPanel() {
    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        graphics.color = when (severity) {
            "high" -> Color.decode("#C75450")
            "medium" -> Color.decode("#EDA200")
            "low" -> Color.decode("#6E6E6E")
            else -> UIUtil.getPanelBackground()
        }

        graphics.fillRoundRect(0, 0, 150, this.height, 5, 5)
        graphics.fillRect(0, 0, 20, this.height)
    }
}
