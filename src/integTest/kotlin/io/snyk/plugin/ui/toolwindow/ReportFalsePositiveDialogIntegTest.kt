package io.snyk.plugin.ui.toolwindow

import UIComponentFinder
import com.intellij.CommonBundle
import com.intellij.openapi.ui.DialogWrapper.CLOSE_EXIT_CODE
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
        val dialog = ReportFalsePositiveDialog(project, titlePanel, emptySet())

        assertEquals(
            "Title should be: `$TITLE_TEXT`",
            TITLE_TEXT,
            dialog.title
        )
    }

    @Test
    fun `test dialog has ordered Cancel and Report buttons`() {
        val titlePanel = JPanel()
        val dialog = ReportFalsePositiveDialog(project, titlePanel, emptySet())
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
        val dialog = ReportFalsePositiveDialog(project, titlePanel, emptySet())
        val centerPanel = dialog.centerPanel

        val foundTitlePanel = UIComponentFinder.getComponentByCondition(centerPanel, JPanel::class) {
            it == titlePanel
        }
        assertNotNull("Given titlePanel not found", foundTitlePanel)
    }

    @Test
    fun `test dialog has upload warning message`() {
        val titlePanel = JPanel()
        val dialog = ReportFalsePositiveDialog(project, titlePanel, emptySet())
        val centerPanel = dialog.centerPanel

        val foundWarningMessageLabel = UIComponentFinder.getComponentByCondition(centerPanel, JBLabel::class) {
            it.text == WARN_MESSAGE_TEXT
        }
        assertNotNull("Upload warning message not found", foundWarningMessageLabel)
    }

    @Test
    fun `test Report False Positive dialog return corresponded files name and content`() {
        val titlePanel = JPanel()
        val file1Name = "fake_file1.java"
        val file1Content = "fake_file1_content"
        val psiFile1 = myFixture.configureByText(file1Name, file1Content)
        val file2Name = "fake_file2.java"
        val file2Content = "fake_file2_content"
        val psiFile2 = myFixture.configureByText(file2Name, file2Content)

        val dialog = ReportFalsePositiveDialog(project, titlePanel, setOf(psiFile1, psiFile2))
        val actions = dialog.actions

        val reportAction = actions.find {
            it.getValue(Action.NAME) == REPORT_FALSE_POSITIVE_TEXT
        }
        assertNotNull("`$REPORT_FALSE_POSITIVE_TEXT` action/button not found", reportAction)
        reportAction!!

        reportAction.actionPerformed(null)
        assertTrue(
            "Files name should be included in resulted concatenated text",
            dialog.result.contains(file1Name) && dialog.result.contains(file2Name)
        )
        assertTrue(
            "Files content should be included in resulted concatenated text",
            dialog.result.contains(file1Content) && dialog.result.contains(file2Content)
        )

        dialog.close(CLOSE_EXIT_CODE)
    }
}
