@file:Suppress("FunctionName")

package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefBrowser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.snyk.plugin.Severity
import io.snyk.plugin.SnykFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.waitForPanelInit
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import snyk.UIComponentFinder.getJLabelByText
import snyk.common.annotator.SnykCodeAnnotator
import snyk.common.lsp.IssueData
import snyk.common.lsp.ScanIssue
import java.nio.file.Paths
import javax.swing.JLabel

class SuggestionDescriptionPanelFromLSOSSTest : BasePlatformTestCase() {
    private lateinit var cut: SuggestionDescriptionPanel
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

        val matchingIssue = mockk<IssueData>()
        every { matchingIssue.name } returns "Another test name"
        every { matchingIssue.from } returns listOf("from")
        every { matchingIssue.upgradePath } returns listOf("upgradePath")

        val matchingIssues = listOf(matchingIssue)

        issue = mockk<ScanIssue>()
        every { issue.getSeverityAsEnum() } returns Severity.CRITICAL
        every { issue.title() } returns "title"
        every { issue.issueNaming() } returns "issueNaming"
        every { issue.cwes() } returns emptyList()
        every { issue.cves() } returns emptyList()
        every { issue.cvssV3() } returns "cvssScore"
        every { issue.cvssScore() } returns "cvssScore"
        every { issue.id() } returns "id"
        every { issue.id } returns "test-issue-id"
        every { issue.ruleId() } returns "ruleId"
        every { issue.filterableIssueType } returns ScanIssue.OPEN_SOURCE
        every { issue.additionalData.name } returns "Test name"
        every { issue.additionalData.matchingIssues } returns matchingIssues
        every { issue.additionalData.fixedIn } returns listOf("fixedIn")
        every { issue.additionalData.exploit } returns "exploit"
        every { issue.additionalData.description } returns "description"
        every {
            issue.additionalData.dataFlow
        } returns emptyList()
    }

    fun `test createUI should build panel with HTML from details if allowed`() {
        val mockJBCefBrowserComponent = JLabel("<html>HTML message</html>")
        val mockJBCefBrowser : JBCefBrowser = mockk()
        every { mockJBCefBrowser.component } returns mockJBCefBrowserComponent
        mockkObject(JCEFUtils)
        every {
            JCEFUtils.getJBCefBrowserIfSupported(eq("<html>HTML message</html>"), any())
        } returns mockJBCefBrowser
        every { issue.details(any()) } returns "<html>HTML message</html>"

        cut = SuggestionDescriptionPanel(project, issue)
        waitForPanelInit(cut)

        val actual = getJLabelByText(cut, "<html>Test message</html>")
        assertNull(actual)

        val actualBrowser = getJLabelByText(cut, "<html>HTML message</html>")
        assertNotNull(actualBrowser)
    }

    fun `test getStyledHTML should inject CSS into the HTML if allowed`() {
        every { issue.details(any()) } returns "<html><head><style>STYLE_PLACEHOLDER</style></head>HTML message</html>"

        cut = SuggestionDescriptionPanel(project, issue)

        val actual = cut.getCustomCssAndScript()

        // we don't apply any custom style for oss
        assertFalse(actual.contains("\${ideStyle}"))
        assertFalse(actual.contains("\${ideScript}"))
        assertFalse(actual.contains(".ignore-warning"))
    }
}
