package snyk.iac

import snyk.common.IgnoreService
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton

class IgnoreButtonActionListener(
    private val ignoreService: IgnoreService,
    val issue: IacIssue
) :
    ActionListener {
    override fun actionPerformed(e: ActionEvent?) {
        ignoreService.ignore(issue)
        (e?.source as? JButton)?.apply {
            isEnabled = false
            text = "Issue now ignored"
        }
    }
}
