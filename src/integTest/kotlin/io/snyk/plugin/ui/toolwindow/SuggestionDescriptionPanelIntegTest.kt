package io.snyk.plugin.ui.toolwindow

import UIComponentFinder
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.toolwindow.ReportFalsePositiveDialog.Companion.REPORT_FALSE_POSITIVE_TEXT
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

    @Test
    fun `test Report False Positive button should be visible if corresponding feature flag is enabled`() {
        val oldFlagValue = Registry.`is`("snyk.code.report.false.positives.enabled", false)
        Registry.get("snyk.code.report.false.positives.enabled").setValue(true)
        try {
            myFixture.configureByText("fake.file", "fake file content")
            val snykCodeFile = SnykCodeFile(project, myFixture.file.virtualFile)
            val cut = SuggestionDescriptionPanel(
                snykCodeFile,
                suggestionForFile,
                0
            )
            val falsePositiveButton = UIComponentFinder.getComponentByCondition(cut, JButton::class) {
                it.text == REPORT_FALSE_POSITIVE_TEXT
            }
            assertNotNull("`$REPORT_FALSE_POSITIVE_TEXT` button not found", falsePositiveButton)
        } finally {
            Registry.get("snyk.code.report.false.positives.enabled").setValue(oldFlagValue)
        }
    }
}
