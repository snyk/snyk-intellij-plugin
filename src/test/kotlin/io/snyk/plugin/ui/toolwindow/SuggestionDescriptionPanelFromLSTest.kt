@file:Suppress("FunctionName")
package io.snyk.plugin.ui.toolwindow

import com.google.gson.Gson
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.impl.DocumentCommitProcessor
import com.intellij.psi.stubs.StubIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.ActionLink
import com.intellij.util.indexing.FileBasedIndex
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.DEFAULT_TIMEOUT_FOR_SCAN_WAITING_MS
import io.snyk.plugin.Severity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanelFromLS
import io.snyk.plugin.ui.toolwindow.panels.VulnerabilityDescriptionPanel
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import snyk.UIComponentFinder.getJBCEFBrowser
import snyk.UIComponentFinder.getJButtonByText
import snyk.UIComponentFinder.getJLabelByText
import snyk.code.annotator.SnykCodeAnnotator
import snyk.common.lsp.CommitChangeLine
import snyk.common.lsp.DataFlow
import snyk.common.lsp.ExampleCommitFix
import snyk.common.lsp.ScanIssue
import snyk.oss.Vulnerability
import java.io.FileReader
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
