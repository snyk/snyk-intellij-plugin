package io.snyk.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBList
import io.snyk.plugin.services.SnykCliService
import io.snyk.plugin.cli.CliResult
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel

/**
 * Main panel for Snyk tool window.
 */
class SnykToolWindowPanel(project: Project) : JPanel() {

    init {
        layout = BorderLayout()

        val cliResult: CliResult = SnykCliService(project).scan()

        val vulnerabilitiesListModel = DefaultListModel<String>()
        val vulnerabilitiesList = JBList<String>(vulnerabilitiesListModel)

        for (vulnerability in cliResult.vulnerabilities) {
            vulnerabilitiesListModel.addElement(vulnerability.title)
        }

        add(vulnerabilitiesList, BorderLayout.CENTER)
    }
}
