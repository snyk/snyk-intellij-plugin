package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBus
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykShowIssueDetailListener
import io.snyk.plugin.getDocument
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.toVirtualFileOrNull
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ShowDocumentParams
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.progress.ProgressManager
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService
import java.nio.file.Files
import java.nio.file.Paths


class SnykLanguageClientTest {
    private lateinit var cut: SnykLanguageClient
    private var snykPluginDisposable: SnykPluginDisposable? = null

    private val applicationMock: Application = mockk()
    private val projectMock: Project = mockk()
    private val settings = SnykApplicationSettingsStateService()
    private val trustServiceMock = mockk<WorkspaceTrustService>(relaxed = true)
    private val dumbServiceMock = mockk<DumbService>()
    private val messageBusMock = mockk<MessageBus>()
    private val projectManagerMock = mockk<ProjectManager>()

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic(ApplicationManager::class)

        every { ApplicationManager.getApplication() } returns applicationMock
        every { applicationMock.getService(WorkspaceTrustService::class.java) } returns trustServiceMock
        every { applicationMock.getService(ProjectManager::class.java) } returns projectManagerMock
        every { applicationMock.getService(ProgressManager::class.java) } returns mockk(relaxed = true)
        every { applicationMock.messageBus } returns mockk(relaxed = true)

        snykPluginDisposable = SnykPluginDisposable()
        every { applicationMock.getService(SnykPluginDisposable::class.java) } returns snykPluginDisposable!!

        every { projectManagerMock.openProjects } returns arrayOf(projectMock)
        every { projectMock.isDisposed } returns false
        every { projectMock.getService(DumbService::class.java) } returns dumbServiceMock
        every { projectMock.messageBus} returns messageBusMock
        every { messageBusMock.isDisposed } returns false
        every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockk(relaxed = true)
        every { dumbServiceMock.isDumb } returns false

        every { pluginSettings() } returns settings

        mockkStatic("snyk.PluginInformationKt")
        every { pluginInfo } returns mockk(relaxed = true)
        every { pluginInfo.integrationName } returns "Snyk Intellij Plugin"
        every { pluginInfo.integrationVersion } returns "2.4.61"
        every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
        every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"


        cut = SnykLanguageClient(projectMock, mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun applyEdit() {}

    @Test
    fun `refreshCodeLenses does not run when disposed`() {
        every { applicationMock.isDisposed } returns true

        cut.refreshCodeLenses()

        verify(exactly = 0) { projectManagerMock.openProjects }
    }

    @Test
    fun `refreshInlineValues does not run when disposed`() {
        every { applicationMock.isDisposed } returns true

        cut.refreshCodeLenses()

        verify(exactly = 0) { projectManagerMock.openProjects }
    }

    @Test
    fun `snykScan does not run when disposed`() {
        every { applicationMock.isDisposed } returns true
        every { projectMock.isDisposed } returns true
        val param = SnykScanParams("success", "code", "testFolder")

        cut.snykScan(param)

        // you cannot display / forward the issues without mapping them to open projects
        verify(exactly = 0) { projectManagerMock.openProjects }
    }

    @Test
    fun `hasAuthenticated does not run when disposed`() {
        every { applicationMock.isDisposed } returns true
        every { projectMock.isDisposed } returns true

        val unexpected = "abc"
        val url = "https://snyk.api.io"
        cut.hasAuthenticated(HasAuthenticatedParam(unexpected, url))

        assertNotEquals(unexpected, settings.token)
    }

    @Test
    fun `addTrustedPaths should not run when disposed`() {
        every { applicationMock.isDisposed } returns true
        every { projectMock.isDisposed } returns true

        val unexpected = "abc"
        cut.addTrustedPaths(SnykTrustedFoldersParams(listOf(unexpected)))

        verify(exactly = 0) { applicationMock.getService(WorkspaceTrustService::class.java) }
    }

    @Test
    fun `addTrustedPaths should add path to trusted paths`() {
        val path = "abc"
        cut.addTrustedPaths(SnykTrustedFoldersParams(listOf(path)))

        verify { trustServiceMock.addTrustedPath(Paths.get(path)) }
    }

    @Test
    fun testGetScanIssues_validDiagnostics() {
        val tempFile = Files.createTempFile("testGetScanIssues", ".java")
        val filePath = tempFile.fileName.toString()
        val vf = mockk<VirtualFile>(relaxed = true)
        every { filePath.toVirtualFile() } returns vf
        val document = mockk<Document>(relaxed = true)
        every { vf.getDocument() } returns document
        every { document.getLineStartOffset(any())} returns 0

        val uri = tempFile.toUri().toString()
        val range = Range(Position(0, 0), Position(0, 1))
        // Create mock PublishDiagnosticsParams with diagnostics list
        val diagnostics: MutableList<Diagnostic> = ArrayList()
        diagnostics.add(createMockDiagnostic(range, "Some Issue", filePath))
        diagnostics.add(createMockDiagnostic(range, "Another Issue", filePath))
        val diagnosticsParams = PublishDiagnosticsParams(uri, diagnostics)

        // Call scanIssues function
        val result: Set<ScanIssue> = cut.getScanIssues(diagnosticsParams)

        // Assert the returned list contains parsed ScanIssues
        assertEquals(2, result.size)
        assertTrue(result.any { it.title == "Some Issue" })
        assertTrue(result.any { it.title == "Another Issue" })
    }

    @Test
    fun `publishDiagnostics should handle non-existent files gracefully`() {
        val nonExistentUri = "file:///tmp/non_existent_file_12345.txt"
        every { nonExistentUri.toVirtualFileOrNull() } returns null

        val mockListener = mockk<SnykScanListener>(relaxed = true)
        every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

        val diagnostics: MutableList<Diagnostic> = ArrayList()
        val range = Range(Position(0, 0), Position(0, 1))
        diagnostics.add(createMockDiagnostic(range, "Some Issue", nonExistentUri))
        val diagnosticsParams = PublishDiagnosticsParams(nonExistentUri, diagnostics)

        // Should not throw exception
        cut.publishDiagnostics(diagnosticsParams)

        // Verify that no diagnostics were published since file doesn't exist
        verify(exactly = 0) { mockListener.onPublishDiagnostics(any(), any(), any()) }
    }

    @Test
    fun `showDocument should only be intercepted for Snyk AI Fix URIs`() {

        fun checkShowDocument(url: String, expectIntercept: Boolean, expectedNotifications: Int = 0) {
            val mockListener = mockk<SnykShowIssueDetailListener>()
            every { mockListener.onShowIssueDetail(any()) } returns Unit
            every { messageBusMock.syncPublisher(SnykShowIssueDetailListener.SHOW_ISSUE_DETAIL_TOPIC) } returns mockListener

            if (expectIntercept) {
                assertEquals(cut.showDocument(ShowDocumentParams(url)).get().isSuccess, expectedNotifications > 0)
            } else {
                assertThrows(UnsupportedOperationException::class.java) { cut.showDocument(ShowDocumentParams(url)) }
            }
            verify(exactly = expectedNotifications) { mockListener.onShowIssueDetail(any()) }
        }

        // HTTP URL should bypass Snyk handler.
        checkShowDocument(
            "http:///temp/test.txt?product=Snyk+Code&issueId=12345&action=showInDetailPanel", expectIntercept = false)

        // Snyk URL with invalid action should bypass Snyk Handler.
        checkShowDocument(
            "snyk:///temp/test.txt?product=Snyk+Code&issueId=12345&action=eatPizza", expectIntercept = false)

        // Snyk URL with non-code product should bypass Snyk handler.
        checkShowDocument(
            "snyk:///temp/test.txt?product=Snyk+IaC&issueId=12345&action=showInDetailPanel", expectIntercept = false)

        // Snyk URL with no issue ID should be handled but not trigger notifications.
        checkShowDocument(
            "snyk:///temp/test.txt?product=Snyk+Code&action=showInDetailPanel",
            expectIntercept = true,
            expectedNotifications = 0)

        // Valid Snyk URL should invoke Snyk handler.
        checkShowDocument(
            "snyk:///temp/test.txt?product=Snyk+Code&issueId=12345&action=showInDetailPanel",
            expectIntercept = true,
            expectedNotifications = 1)
    }

    private fun createMockDiagnostic(range: Range, id: String, filePath: String): Diagnostic {
        val rangeString = Gson().toJson(range)
        val jsonString = """{"id": "$id", "title": "$id", "filePath": "$filePath", "range": $rangeString, "severity": "medium", "filterableIssueType": "Open Source", "isIgnored": false, "isNew": false, "additionalData": {"ruleId": "test-rule-id", "key": "test-key", "message": "Mock message", "isSecurityType": false, "rule": "mock-rule", "repoDatasetSize": 0, "exampleCommitFixes": [], "text": "", "priorityScore": 0, "hasAIFix": false, "dataFlow": [], "description": "", "language": "kotlin", "packageManager": "gradle", "packageName": "mock-package", "name": "mock-name", "version": "1.0.0", "from": [], "upgradePath": [], "isPatchable": false, "isUpgradable": false, "projectName": "test-project", "matchingIssues": [], "publicId": "test-public-id"}}"""
        val mockDiagnostic = Diagnostic()
        mockDiagnostic.range = range
        mockDiagnostic.data = jsonString
        return mockDiagnostic
    }

}
