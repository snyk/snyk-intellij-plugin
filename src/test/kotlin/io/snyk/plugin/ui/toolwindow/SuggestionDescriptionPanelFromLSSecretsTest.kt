@file:Suppress("FunctionName")

package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefBrowser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.snyk.plugin.Severity
import io.snyk.plugin.SnykFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.LoadHandlerGenerator
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import io.snyk.plugin.waitForPanelInit
import java.nio.file.Paths
import javax.swing.JLabel
import snyk.UIComponentFinder.getJLabelByText
import snyk.common.annotator.SnykCodeAnnotator
import snyk.common.lsp.ScanIssue

class SuggestionDescriptionPanelFromLSSecretsTest : BasePlatformTestCase() {
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
    every { issue.filterableIssueType } returns ScanIssue.SECRETS
    every { issue.additionalData.message } returns "Test message"
  }

  fun `test SECRETS issue registers SubmitIgnoreRequestHandler so the create-ignore form submit works`() {
    val mockJBCefBrowserComponent = JLabel("<html>HTML message</html>")
    val mockJBCefBrowser: JBCefBrowser = mockk(relaxed = true)
    every { mockJBCefBrowser.component } returns mockJBCefBrowserComponent

    val handlersSlot = slot<List<LoadHandlerGenerator>>()
    mockkObject(JCEFUtils)
    every { JCEFUtils.getJBCefBrowserIfSupported(any(), capture(handlersSlot)) } returns
      mockJBCefBrowser

    every { issue.details(project) } returns "<html>HTML message</html>"
    cut = SuggestionDescriptionPanel(project, issue)
    waitForPanelInit(cut)

    assertNotNull(getJLabelByText(cut, "<html>HTML message</html>"))
    assertTrue(
      "SECRETS issue must wire at least one load handler so the submit-ignore JS bridge is registered",
      handlersSlot.captured.isNotEmpty(),
    )
  }
}
