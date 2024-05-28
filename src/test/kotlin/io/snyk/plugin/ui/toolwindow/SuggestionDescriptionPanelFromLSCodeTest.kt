@file:Suppress("FunctionName")
package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.snyk.plugin.Severity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.SnykFile
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanelFromLS
import junit.framework.TestCase
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.Before
import org.junit.Test
import snyk.UIComponentFinder.getJBCEFBrowser
import snyk.UIComponentFinder.getJLabelByText
import snyk.UIComponentFinder.getJPanelByName
import snyk.code.annotator.SnykCodeAnnotator
import snyk.common.ProductType
import snyk.common.lsp.CommitChangeLine
import snyk.common.lsp.DataFlow
import snyk.common.lsp.ExampleCommitFix
import snyk.common.lsp.ScanIssue
import java.nio.file.Paths
import javax.swing.JLabel

class SuggestionDescriptionPanelFromLSCodeTest : BasePlatformTestCase() {
    private lateinit var cut: SuggestionDescriptionPanelFromLS
    private val fileName = "app.js"
    private lateinit var snykFile: SnykFile
    private lateinit var issue: ScanIssue

    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    override fun getTestDataPath(): String {
        val resource = SnykCodeAnnotator::class.java.getResource("/test-fixtures/code/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        snykFile = SnykFile(psiFile.project, psiFile.virtualFile)

        issue = mockk<ScanIssue>()
        every { issue.getSeverityAsEnum() } returns Severity.CRITICAL
        every { issue.title() } returns "title"
        every { issue.issueNaming() } returns "issueNaming"
        every { issue.cwes() } returns emptyList()
        every { issue.cves() } returns emptyList()
        every { issue.cvssV3() } returns null
        every { issue.cvssScore() } returns null
        every { issue.id() } returns "id"
        every { issue.additionalData.getProductType() } returns ProductType.CODE_SECURITY
        every { issue.additionalData.message } returns "Test message"
        every { issue.additionalData.repoDatasetSize } returns 1
        every { issue.additionalData.exampleCommitFixes } returns listOf(ExampleCommitFix("https://commit-url", listOf(
            CommitChangeLine("1", 1, "lineChange")
        )))
        every { issue.additionalData.dataFlow } returns listOf(DataFlow(0, getTestDataPath(), Range(Position(1, 1), Position(1, 1)), ""))
    }

    @Test
    fun `test createUI should build the right panels for Snyk Code`() {
        cut = SuggestionDescriptionPanelFromLS(snykFile, issue)

        val issueNaming = getJLabelByText(cut, issue.issueNaming())
        assertNotNull(issueNaming)

        val overviewPanel = getJLabelByText(cut, "<html>Test message</html>")
        assertNotNull(overviewPanel)

        val dataFlowPanel = getJPanelByName(cut, "dataFlowPanel")
        assertNotNull(dataFlowPanel)

        val fixExamplesPanel = getJPanelByName(cut, "fixExamplesPanel")
        assertNotNull(fixExamplesPanel)

        val introducedThroughPanel = getJPanelByName(cut, "introducedThroughPanel")
        assertNull(introducedThroughPanel)

        val detailedPathsPanel = getJPanelByName(cut, "detailedPathsPanel")
        assertNull(detailedPathsPanel)

        val ossOverviewPanel = getJPanelByName(cut, "overviewPanel")
        assertNull(ossOverviewPanel)
    }

    @Test
    fun `test createUI should build panel with issue message as overview label if the feature flag is not enabled`() {
        every { issue.details() } returns ""
        cut = SuggestionDescriptionPanelFromLS(snykFile, issue)

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNotNull(actual)

        val actualBrowser = getJBCEFBrowser(cut)
        assertNull(actualBrowser)
    }

    @Test
    fun `test createUI should build panel with issue message as overview label if HTML is not allowed, even if the feature flag is enabled`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        every { issue.canLoadSuggestionPanelFromHTML() } returns false
        cut = SuggestionDescriptionPanelFromLS(snykFile, issue)

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNotNull(actual)

        val actualBrowser = getJBCEFBrowser(cut)
        assertNull(actualBrowser)
    }

    @Test
    fun `test createUI should show nothing if feature flag is enabled but JCEF is not`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        mockkObject(JCEFUtils)
        every { JCEFUtils.getJBCefBrowserComponentIfSupported(eq("<html>HTML message</html>"), any()) } returns null

        every { issue.details() } returns "<html>HTML message</html>"
        every { issue.canLoadSuggestionPanelFromHTML() } returns true
        cut = SuggestionDescriptionPanelFromLS(snykFile, issue)

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNull(actual)

        val actualBrowser = getJBCEFBrowser(cut)
        assertNull(actualBrowser)
    }

    @Test
    fun `test createUI should build panel with HTML from details if feature flag is enabled`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        val mockJBCefBrowserComponent = JLabel("<html>HTML message</html>")
        mockkObject(JCEFUtils)
        every { JCEFUtils.getJBCefBrowserComponentIfSupported(eq("<html>HTML message</html>"), any()) } returns mockJBCefBrowserComponent

        every { issue.details() } returns "<html>HTML message</html>"
        every { issue.canLoadSuggestionPanelFromHTML() } returns true
        cut = SuggestionDescriptionPanelFromLS(snykFile, issue)

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNull(actual)

        val actualBrowser = getJLabelByText(cut, "<html>HTML message</html>")
        assertNotNull(actualBrowser)
    }

    @Test
    fun `test getStyledHTML should inject CSS into the HTML`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        every { issue.details() } returns "<html><head><style>\${ideStyle}</style></head>HTML message</html>"
        every { issue.canLoadSuggestionPanelFromHTML() } returns true
        cut = SuggestionDescriptionPanelFromLS(snykFile, issue)

        val actual = cut.getStyledHTML()
        assertFalse(actual.contains("\${ideStyle}"))
        assertTrue(actual.contains(".ignore-warning"))
    }
}
