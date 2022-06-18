package io.snyk.plugin.ui.toolwindow

import snyk.common.UIComponentFinder
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.toolwindow.ReportFalsePositiveDialog.Companion.REPORT_FALSE_POSITIVE_TEXT
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import org.junit.Test
import javax.swing.JButton

@Suppress("FunctionName")
class SuggestionDescriptionPanelIntegTest : BasePlatformTestCase() {

    private val suggestionForFile = SuggestionForFile(
        "id",
        "rule",
        "message",
        "title",
        "text",
        0,
        0,
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList()
    )

    private fun runReportFalsePositiveRelatedTest(runnable: () -> Unit) {
        val oldFlagValue = Registry.`is`("snyk.code.report.false.positives.enabled", false)
        Registry.get("snyk.code.report.false.positives.enabled").setValue(true)
        val oldReportFalsePositivesEnabled = pluginSettings().reportFalsePositivesEnabled
        pluginSettings().reportFalsePositivesEnabled = true
        try {
            runnable.invoke()
        } finally {
            Registry.get("snyk.code.report.false.positives.enabled").setValue(oldFlagValue)
            pluginSettings().reportFalsePositivesEnabled = oldReportFalsePositivesEnabled
        }
    }

    @Test
    fun `test Report False Positive button should be visible if corresponding feature flag is enabled`() {
        myFixture.configureByText("fake.file", "fake file content")
        val snykCodeFile = SnykCodeFile(project, myFixture.file.virtualFile)
        runReportFalsePositiveRelatedTest {
            val cut = SuggestionDescriptionPanel(
                snykCodeFile,
                suggestionForFile,
                0
            )
            val falsePositiveButton = UIComponentFinder.getComponentByCondition(cut, JButton::class) {
                it.text == REPORT_FALSE_POSITIVE_TEXT
            }
            assertNotNull("`$REPORT_FALSE_POSITIVE_TEXT` button not found", falsePositiveButton)
        }
    }

    @Test
    fun `test Allow reporting false positive only with existing entitlement`() {
        myFixture.configureByText("fake.file", "fake file content")
        val snykCodeFile = SnykCodeFile(project, myFixture.file.virtualFile)
        runReportFalsePositiveRelatedTest {

            pluginSettings().reportFalsePositivesEnabled = false

            val cut = SuggestionDescriptionPanel(
                snykCodeFile,
                suggestionForFile,
                0
            )
            val falsePositiveButton = UIComponentFinder.getComponentByCondition(cut, JButton::class) {
                it.text == REPORT_FALSE_POSITIVE_TEXT
            }
            assertNull("`$REPORT_FALSE_POSITIVE_TEXT` button should not be found", falsePositiveButton)
        }
    }
}
