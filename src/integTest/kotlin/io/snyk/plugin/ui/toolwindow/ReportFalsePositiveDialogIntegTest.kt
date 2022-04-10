package io.snyk.plugin.ui.toolwindow

import com.intellij.CommonBundle
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import io.snyk.plugin.ui.toolwindow.ReportFalsePositiveDialog.Companion.REPORT_FALSE_POSITIVE_TEXT
import io.snyk.plugin.ui.toolwindow.ReportFalsePositiveDialog.Companion.TITLE_TEXT
import io.snyk.plugin.ui.toolwindow.ReportFalsePositiveDialog.Companion.WARN_MESSAGE_TEXT
import org.junit.Test
import javax.swing.Action
import javax.swing.JPanel

@Suppress("FunctionName")
class ReportFalsePositiveDialogIntegTest : BasePlatformTestCase() {

    @Test
    fun `test dialog has required title`() {
        val titlePanel = JPanel()
        val dialog = ReportFalsePositiveDialog(project, titlePanel)

        assertEquals(
            "Title should be: `$TITLE_TEXT`",
            TITLE_TEXT,
            dialog.title
        )
    }

    @Test
    fun `test dialog has ordered Cancel and Report buttons`() {
        val titlePanel = JPanel()
        val dialog = ReportFalsePositiveDialog(project, titlePanel)
        val actions = dialog.actions

        assertEquals(
            "Should be 2 actions/buttons",
            2,
            actions.size
        )
        assertEquals(
            "First action/button should be `Cancel`",
            CommonBundle.getCancelButtonText(),
            actions[0].getValue(Action.NAME)
        )
        assertEquals(
            "Second action/button should be `$REPORT_FALSE_POSITIVE_TEXT`",
            REPORT_FALSE_POSITIVE_TEXT,
            actions[1].getValue(Action.NAME)
        )
    }

    @Test
    fun `test dialog has given titlePanel`() {
        val titlePanel = JPanel()
        val dialog = ReportFalsePositiveDialog(project, titlePanel)
        val centerPanel = dialog.centerPanel

        val foundTitlePanel = UIComponentFinder.getComponentByCondition(centerPanel, JPanel::class) {
            it == titlePanel
        }
        assertNotNull("Given titlePanel not found", foundTitlePanel)
    }

    @Test
    fun `test dialog has upload warning message`() {
        val titlePanel = JPanel()
        val dialog = ReportFalsePositiveDialog(project, titlePanel)
        val centerPanel = dialog.centerPanel

        val foundWarningMessageLabel = UIComponentFinder.getComponentByCondition(centerPanel, JBLabel::class) {
            it.text == WARN_MESSAGE_TEXT
        }
        assertNotNull("Upload warning message not found", foundWarningMessageLabel)
    }
}
