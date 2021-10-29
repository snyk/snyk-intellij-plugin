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
        (e?.source as? JButton)?.apply {
            isEnabled = false
            text = "Issue now ignored"
        }
    }
}
