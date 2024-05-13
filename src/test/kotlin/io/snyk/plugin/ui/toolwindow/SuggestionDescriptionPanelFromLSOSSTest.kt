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
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.Before
import org.junit.Test
import snyk.UIComponentFinder.getActionLinkByText
import snyk.UIComponentFinder.getJBCEFBrowser
import snyk.UIComponentFinder.getJButtonByText
import snyk.UIComponentFinder.getJLabelByText
import snyk.UIComponentFinder.getJPanelByName
import snyk.code.annotator.SnykCodeAnnotator
import snyk.common.ProductType
import snyk.common.lsp.CommitChangeLine
import snyk.common.lsp.DataFlow
import snyk.common.lsp.ExampleCommitFix
import snyk.common.lsp.IssueData
import snyk.common.lsp.ScanIssue
import java.nio.file.Paths
import javax.swing.JLabel

class SuggestionDescriptionPanelFromLSOSSTest : BasePlatformTestCase() {
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

    @Before
    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        snykFile = SnykFile(psiFile.project, psiFile.virtualFile)

        val matchingIssue = mockk<IssueData>()
        every { matchingIssue.getProductType() } returns ProductType.OSS
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
        every { issue.ruleId() } returns "ruleId"
        every { issue.additionalData.getProductType() } returns ProductType.OSS
        every { issue.additionalData.name } returns "Test name"
        every { issue.additionalData.matchingIssues } returns matchingIssues
        every { issue.additionalData.fixedIn } returns listOf("fixedIn")
        every { issue.additionalData.exploit } returns "exploit"
        every { issue.additionalData.description } returns "description"
    }

    @Test
    fun `test createUI should build the right panels for Snyk OSS`() {
        cut = SuggestionDescriptionPanelFromLS(snykFile, issue)

        val issueNaming = getJLabelByText(cut, issue.issueNaming())
        assertNotNull(issueNaming)

        val cvssScore = getActionLinkByText(cut, "CVSS cvssScore")
        assertNotNull(cvssScore)

        val ruleId = getActionLinkByText(cut, "ID")
        assertNotNull(ruleId)

        val overviewPanel = getJLabelByText(cut, "<html>Test message</html>")
        assertNull(overviewPanel)

        val dataFlowPanel = getJPanelByName(cut, "dataFlowPanel")
        assertNull(dataFlowPanel)

        val fixExamplesPanel = getJPanelByName(cut, "fixExamplesPanel")
        assertNull(fixExamplesPanel)

        val introducedThroughPanel = getJPanelByName(cut, "introducedThroughPanel")
        assertNotNull(introducedThroughPanel)

        val detailedPathsPanel = getJPanelByName(cut, "detailedPathsPanel")
        assertNotNull(detailedPathsPanel)

        val ossOverviewPanel = getJPanelByName(cut, "overviewPanel")
        assertNotNull(ossOverviewPanel)
    }
}
