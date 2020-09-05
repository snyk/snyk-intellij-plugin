package io.snyk.plugin.ui.toolwindow

import com.intellij.ui.ScrollPaneFactory
import io.snyk.plugin.cli.Vulnerability
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

class FullDescriptionPanel : JPanel() {
    init {
        layout = BorderLayout()

        displaySelectVulnerabilityMessage()
    }

    fun displayDescription(vulnerability: Vulnerability) {
        removeAll()

        add(ScrollPaneFactory.createScrollPane(VulnerabilityDescriptionPanel(vulnerability)), BorderLayout.CENTER)

        revalidate()
    }

    fun displaySelectVulnerabilityMessage() {
        removeAll()

        add(CenterOneComponentPanel(JLabel("Please, select vulnerability")), BorderLayout.CENTER)

        revalidate()
    }
}
