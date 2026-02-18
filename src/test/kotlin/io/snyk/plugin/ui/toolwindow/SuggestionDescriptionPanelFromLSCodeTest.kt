@file:Suppress("FunctionName")

package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefBrowser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.Severity
import io.snyk.plugin.SnykFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import io.snyk.plugin.waitForPanelInit
import java.nio.file.Paths
import javax.swing.JLabel
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import snyk.UIComponentFinder.getJBCEFBrowser
import snyk.UIComponentFinder.getJLabelByText
import snyk.common.annotator.SnykCodeAnnotator
import snyk.common.lsp.CommitChangeLine
import snyk.common.lsp.DataFlow
import snyk.common.lsp.ExampleCommitFix
import snyk.common.lsp.ScanIssue

class SuggestionDescriptionPanelFromLSCodeTest : BasePlatformTestCase() {
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
    unmockkAll()
    super.setUp()
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
    every { issue.id } returns "test-issue-id"
    every { issue.filterableIssueType } returns ScanIssue.CODE_SECURITY
    every { issue.additionalData.message } returns "Test message"
    every { issue.additionalData.repoDatasetSize } returns 1
    every { issue.additionalData.exampleCommitFixes } returns
      listOf(ExampleCommitFix("https://commit-url", listOf(CommitChangeLine("1", 1, "lineChange"))))
    every { issue.additionalData.dataFlow } returns
      listOf(DataFlow(0, getTestDataPath(), Range(Position(1, 1), Position(1, 1)), ""))
  }

  fun `test createUI should show nothing if HTML is allowed but JCEF is not supported`() {
    mockkObject(JCEFUtils)
    every { JCEFUtils.getJBCefBrowserIfSupported(eq("<html>HTML message</html>"), any()) } returns
      null

    every { issue.details(project) } returns "<html>HTML message</html>"
    cut = SuggestionDescriptionPanel(project, issue)
    waitForPanelInit(cut)

    val actual = getJLabelByText(cut, "<html>Test message</html>")
    assertNull(actual)

    val actualBrowser = getJBCEFBrowser(cut)
    assertNull(actualBrowser)
  }

  fun `test createUI should build panel with HTML from details if allowed`() {
    val mockJBCefBrowserComponent = JLabel("<html>HTML message</html>")
    val mockJBCefBrowser: JBCefBrowser = mockk()
    every { mockJBCefBrowser.component } returns mockJBCefBrowserComponent

    mockkObject(JCEFUtils)
    every { JCEFUtils.getJBCefBrowserIfSupported(eq("<html>HTML message</html>"), any()) } returns
      mockJBCefBrowser

    every { issue.details(project) } returns "<html>HTML message</html>"
    cut = SuggestionDescriptionPanel(project, issue)
    waitForPanelInit(cut)

    val actual = getJLabelByText(cut, "<html>Test message</html>")
    assertNull(actual)

    val actualBrowser = getJLabelByText(cut, "<html>HTML message</html>")
    assertNotNull(actualBrowser)
  }

  fun `test getStyledHTML should inject CSS into the HTML if allowed`() {
    every { issue.details(project) } returns "<html><head>\${ideStyle}</head>HTML message</html>"
    cut = SuggestionDescriptionPanel(project, issue)

    val actual = cut.getCustomCssAndScript()
    assertFalse(actual.contains("\${ideStyle}"))
    assertFalse(actual.contains("\${ideScript}"))
    assertFalse(actual.contains("\${nonce}"))
  }

  fun `test panel implements Disposable`() {
    every { issue.details(project) } returns "<html>HTML message</html>"
    cut = SuggestionDescriptionPanel(project, issue)
    assertTrue("SuggestionDescriptionPanel should implement Disposable", cut is Disposable)
  }

  fun `test dispose should dispose JCEF browser`() {
    val mockJBCefBrowserComponent = JLabel("<html>HTML message</html>")
    val mockJBCefBrowser: JBCefBrowser = mockk(relaxed = true)
    every { mockJBCefBrowser.component } returns mockJBCefBrowserComponent

    mockkObject(JCEFUtils)
    every { JCEFUtils.getJBCefBrowserIfSupported(any(), any()) } returns mockJBCefBrowser

    every { issue.details(project) } returns "<html>HTML message</html>"
    cut = SuggestionDescriptionPanel(project, issue)
    waitForPanelInit(cut)

    (cut as Disposable).dispose()
    verify { mockJBCefBrowser.dispose() }
  }
}
