package snyk.container.ui

import com.intellij.uiDesigner.core.GridLayoutManager
import snyk.container.ContainerIssue
import javax.swing.JLabel
import javax.swing.JPanel

class ContainerIssueDetailPanel(
    private val issue: ContainerIssue
) : JPanel() {
    init {
        this.layout = GridLayoutManager(1, 1)

        val contentPanel = JPanel()
        contentPanel.add(JLabel(issue.id))
    }
}
