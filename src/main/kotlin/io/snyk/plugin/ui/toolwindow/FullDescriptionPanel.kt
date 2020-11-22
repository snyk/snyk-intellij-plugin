package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.Alarm
import io.snyk.plugin.cli.Vulnerability
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

class FullDescriptionPanel : JPanel() {
    private val alarm: Alarm = Alarm()

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

    fun displayDescription(psiFile: PsiFile, suggestion: SuggestionForFile) {
        removeAll()

        val scrollPane = ScrollPaneFactory.createScrollPane(SuggestionDescriptionPanel(psiFile, suggestion))
        add(scrollPane, BorderLayout.CENTER)

        revalidate()

        // Hack to scroll SuggestionDescriptionPanel to the top.
        // Without delay race condition with revalidate() on EDT happened
        alarm.cancelAllRequests()
        alarm.addRequest({
            ApplicationManager.getApplication().invokeLater {
                scrollPane.verticalScrollBar.value = 0
            }
        }, 20)
    }
}
