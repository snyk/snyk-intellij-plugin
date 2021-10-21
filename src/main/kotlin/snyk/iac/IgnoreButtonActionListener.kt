package snyk.iac

import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton

class IgnoreButtonActionListener(
    private val iacIgnoreService: IacIgnoreService,
    val issue: IacIssue
) :
    ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
        iacIgnoreService.ignore(issue)
        if (e != null && e.source is JButton) {
            val jButton = e.source as JButton
            jButton.isEnabled = false
            jButton.text = "Issue now ignored"
        }
    }
}
