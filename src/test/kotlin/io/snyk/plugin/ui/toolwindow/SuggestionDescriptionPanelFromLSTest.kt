@file:Suppress("FunctionName")
package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.Severity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanelFromLS
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.Before
import org.junit.Test
import snyk.UIComponentFinder.getJBCEFBrowser
import snyk.UIComponentFinder.getJLabelByText
import snyk.code.annotator.SnykCodeAnnotator
import snyk.common.lsp.CommitChangeLine
import snyk.common.lsp.DataFlow
import snyk.common.lsp.ExampleCommitFix
import snyk.common.lsp.ScanIssue
import java.nio.file.Paths

class SuggestionDescriptionPanelFromLSTest : BasePlatformTestCase() {
    private lateinit var cut: SuggestionDescriptionPanelFromLS
    private val fileName = "app.js"
    private lateinit var snykCodeFile: SnykCodeFile
    private lateinit var issue: ScanIssue

    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    override fun getTestDataPath(): String {
        val resource = SnykCodeAnnotator::class.java.getResource("/test-fixtures/code/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    @Before
    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        snykCodeFile = SnykCodeFile(psiFile.project, psiFile.virtualFile)

        issue = mockk<ScanIssue>()
        every { issue.additionalData.message } returns "Test message"
        every { issue.additionalData.isSecurityType } returns true
        every { issue.additionalData.cwe } returns null
        every { issue.additionalData.repoDatasetSize } returns 1
        every { issue.additionalData.exampleCommitFixes } returns listOf(ExampleCommitFix("https://commit-url", listOf(
            CommitChangeLine("1", 1, "lineChange")
        )))
        every { issue.additionalData.dataFlow } returns listOf(DataFlow(0, getTestDataPath(), Range(Position(1, 1), Position(1, 1)), ""))
        every { issue.title } returns "Test title"
        every { issue.getSeverityAsEnum() } returns Severity.CRITICAL
    }

    @Test
    fun `test createUI should build panel with issue message as overview label if the feature flag is not enabled`() {
        every { issue.additionalData.details } returns "<html>HTML message</html>"
        every { issue.additionalData.details } returns null
        cut = SuggestionDescriptionPanelFromLS(snykCodeFile, issue)

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNotNull(actual)

        val actualBrowser = getJBCEFBrowser(cut)
        assertNull(actualBrowser)
    }

    @Test
    fun `test createUI should build panel with issue message as overview label if the details are empty even if feature flag is enabled`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        every { issue.additionalData.details } returns null
        cut = SuggestionDescriptionPanelFromLS(snykCodeFile, issue)

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNotNull(actual)

        val actualBrowser = getJBCEFBrowser(cut)
        assertNull(actualBrowser)
    }

    @Test
    fun `test createUI should show nothing if feature flag is enabled but JCEF is not`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        every { issue.additionalData.details } returns "<html>HTML message</html>"
        cut = SuggestionDescriptionPanelFromLS(snykCodeFile, issue)

        Registry.get("ide.browser.jcef.enabled").setValue("false")

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNull(actual)

        val actualBrowser = getJBCEFBrowser(cut)
        assertNull(actualBrowser)
    }

    @Test
    fun `test createUI should build panel with HTML from details if feature flag is enabled`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        every { issue.additionalData.details } returns "<html>HTML message</html>"
        cut = SuggestionDescriptionPanelFromLS(snykCodeFile, issue)

        Registry.get("ide.browser.jcef.enabled").setValue("true")

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNull(actual)

        val actualBrowser = getJBCEFBrowser(cut)
        assertNotNull(actualBrowser)
    }
}
