package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBus
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.events.SnykFolderConfigListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykShowIssueDetailListener
import io.snyk.plugin.events.SnykTreeViewListener
import io.snyk.plugin.getDocument
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForFile
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.toVirtualFileOrNull
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.progress.ProgressManager
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService

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
    mockkStatic(WriteCommandAction::class)

    mockkObject(snyk.common.editor.DocumentChanger)

    every { ApplicationManager.getApplication() } returns applicationMock
    every { applicationMock.getService(WorkspaceTrustService::class.java) } returns trustServiceMock
    every { applicationMock.getService(ProjectManager::class.java) } returns projectManagerMock
    every { applicationMock.getService(ProgressManager::class.java) } returns mockk(relaxed = true)
    every { applicationMock.messageBus } returns mockk(relaxed = true)

    snykPluginDisposable = SnykPluginDisposable()
    every { applicationMock.getService(SnykPluginDisposable::class.java) } returns
      snykPluginDisposable!!

    every { projectManagerMock.openProjects } returns arrayOf(projectMock)
    every { projectMock.isDisposed } returns false
    every { projectMock.getService(DumbService::class.java) } returns dumbServiceMock
    every { projectMock.messageBus } returns messageBusMock
    every { messageBusMock.isDisposed } returns false
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns
      mockk(relaxed = true)
    every { dumbServiceMock.isDumb } returns false

    every { pluginSettings() } returns settings

    mockkStatic("snyk.PluginInformationKt")
    every { pluginInfo } returns mockk(relaxed = true)
    every { pluginInfo.integrationName } returns "Snyk Intellij Plugin"
    every { pluginInfo.integrationVersion } returns "2.4.61"
    every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
    every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"

    cut = SnykLanguageClient(projectMock, mockk(relaxed = true))

    every { WriteCommandAction.runWriteCommandAction(projectMock, any<Runnable>()) } answers
      {
        secondArg<Runnable>().run()
      }
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun applyEdit() {
    val encodedUri = "file:///Users/user%20name/file%20with%20spaces%20%24peci%40l.txt"
    val virtualFile = mockk<VirtualFile>(relaxed = true)
    every { encodedUri.toVirtualFile() } returns virtualFile

    every { snyk.common.editor.DocumentChanger.applyChange(any()) } returns Unit
    every { refreshAnnotationsForFile(projectMock, any()) } returns Unit

    val edits = listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "test"))
    val params = ApplyWorkspaceEditParams(WorkspaceEdit(mapOf(encodedUri to edits)))

    val result = cut.applyEdit(params).get()

    assertTrue(result.isApplied)
    verify(exactly = 1) {
      snyk.common.editor.DocumentChanger.applyChange(match { it.key == encodedUri })
    }
    verify(exactly = 1) { refreshAnnotationsForFile(projectMock, virtualFile) }
  }

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
    every { document.getLineStartOffset(any()) } returns 0

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
      val latch = if (expectedNotifications > 0) CountDownLatch(expectedNotifications) else null

      every { mockListener.onShowIssueDetail(any()) } answers { latch?.countDown() }
      every {
        messageBusMock.syncPublisher(SnykShowIssueDetailListener.SHOW_ISSUE_DETAIL_TOPIC)
      } returns mockListener

      val result = cut.showDocument(ShowDocumentParams(url)).get()
      if (expectIntercept) {
        assertEquals(result.isSuccess, expectedNotifications > 0)
        // Wait for async publishAsync to complete if we expect notifications
        if (expectedNotifications > 0) {
          assertTrue(
            "Async publish should complete within timeout",
            latch!!.await(2, TimeUnit.SECONDS),
          )
        }
      } else {
        assertEquals("Non-intercepted URI should return false", false, result.isSuccess)
      }
      verify(exactly = expectedNotifications) { mockListener.onShowIssueDetail(any()) }
    }

    // HTTP URL should open in browser, not be intercepted by Snyk handler.
    mockkStatic(com.intellij.ide.BrowserUtil::class)
    justRun { com.intellij.ide.BrowserUtil.browse(any<String>()) }
    val httpUrl = "http:///temp/test.txt?product=Snyk+Code&issueId=12345&action=showInDetailPanel"
    val httpResult = cut.showDocument(ShowDocumentParams(httpUrl)).get()
    assertTrue("HTTP URL should be handled by opening browser", httpResult.isSuccess)

    // Snyk URL with invalid action should bypass Snyk Handler.
    checkShowDocument(
      "snyk:///temp/test.txt?product=Snyk+Code&issueId=12345&action=eatPizza",
      expectIntercept = false,
    )

    // Snyk URL with IaC product should be intercepted and trigger notifications.
    checkShowDocument(
      "snyk:///temp/test.txt?product=Snyk+IaC&issueId=12345&action=showInDetailPanel",
      expectIntercept = true,
      expectedNotifications = 1,
    )

    // Snyk URL with no issue ID should be handled but not trigger notifications.
    checkShowDocument(
      "snyk:///temp/test.txt?product=Snyk+Code&action=showInDetailPanel",
      expectIntercept = true,
      expectedNotifications = 0,
    )

    // Valid Snyk URL should invoke Snyk handler.
    checkShowDocument(
      "snyk:///temp/test.txt?product=Snyk+Code&issueId=12345&action=showInDetailPanel",
      expectIntercept = true,
      expectedNotifications = 1,
    )
  }

  @Test
  fun `snykTreeView should publish to tree view topic`() {
    val mockListener = mockk<SnykTreeViewListener>(relaxed = true)
    val latch = CountDownLatch(1)
    every { mockListener.onTreeViewReceived(any()) } answers { latch.countDown() }
    every { messageBusMock.syncPublisher(SnykTreeViewListener.SNYK_TREE_VIEW_TOPIC) } returns
      mockListener

    val params = SnykTreeViewParams(treeViewHtml = "<div>tree</div>", totalIssues = 5)
    cut.snykTreeView(params)

    assertTrue("Async publish should complete within timeout", latch.await(2, TimeUnit.SECONDS))
    verify(exactly = 1) { mockListener.onTreeViewReceived(params) }
  }

  @Test
  fun `snykTreeView should not run when disposed`() {
    every { projectMock.isDisposed } returns true

    val mockListener = mockk<SnykTreeViewListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykTreeViewListener.SNYK_TREE_VIEW_TOPIC) } returns
      mockListener

    val params = SnykTreeViewParams(treeViewHtml = "<div>tree</div>", totalIssues = 5)
    cut.snykTreeView(params)

    Thread.sleep(200)
    verify(exactly = 0) { mockListener.onTreeViewReceived(any()) }
  }

  @Test
  fun `folderConfig should call migrateNestedFolderConfigs after adding configs`() {
    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    val folderConfigListener = mockk<SnykFolderConfigListener>(relaxed = true)
    every {
      messageBusMock.syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
    } returns folderConfigListener

    val folderConfig = FolderConfig(folderPath = "/test/project", baseBranch = "main")
    val param = FolderConfigsParam(listOf(folderConfig))

    cut.folderConfig(param)

    verify(timeout = 5000) { folderConfigSettingsMock.addAll(listOf(folderConfig)) }
    verify(timeout = 5000) { folderConfigSettingsMock.migrateNestedFolderConfigs(projectMock) }
  }

  @Test
  fun `folderConfig should not run when disposed`() {
    every { projectMock.isDisposed } returns true

    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    val param = FolderConfigsParam(listOf(FolderConfig(folderPath = "/test", baseBranch = "main")))
    cut.folderConfig(param)

    verify(exactly = 0) { folderConfigSettingsMock.addAll(any()) }
  }

  @Test
  fun `showDocument should navigate to file URI with selection`() {
    val fileUri = "file:///tmp/test-file.kt"
    val virtualFile = mockk<VirtualFile>(relaxed = true)
    val document = mockk<Document>(relaxed = true)

    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns settings
    every { fileUri.toVirtualFileOrNull() } returns virtualFile
    every { virtualFile.isValid } returns true
    every { virtualFile.getDocument() } returns document
    every { document.lineCount } returns 100
    every { document.getLineStartOffset(any()) } returns 0
    every { io.snyk.plugin.navigateToSource(any(), any(), any(), any()) } returns Unit

    val params =
      ShowDocumentParams(fileUri).apply { selection = Range(Position(5, 0), Position(5, 10)) }

    val result = cut.showDocument(params).get()

    assertTrue(result.isSuccess)
  }

  @Test
  fun `showDocument should open https URL in browser`() {
    mockkStatic(com.intellij.ide.BrowserUtil::class)
    justRun { com.intellij.ide.BrowserUtil.browse(any<String>()) }

    val params = ShowDocumentParams("https://example.com/doc")

    val result = cut.showDocument(params).get()

    assertTrue(result.isSuccess)
    verify { com.intellij.ide.BrowserUtil.browse("https://example.com/doc") }
  }

  @Test
  fun `showDocument should return false for unsupported URI scheme`() {
    val params = ShowDocumentParams("ftp://example.com/file")

    val result = cut.showDocument(params).get()

    assertEquals(false, result.isSuccess)
  }

  @Test
  fun `showDocument should return false for non-existent file`() {
    val fileUri = "file:///nonexistent/path.kt"
    every { fileUri.toVirtualFileOrNull() } returns null

    val params = ShowDocumentParams(fileUri)

    val result = cut.showDocument(params).get()

    assertEquals(false, result.isSuccess)
  }

  private fun createMockDiagnostic(range: Range, id: String, filePath: String): Diagnostic {
    val rangeString = Gson().toJson(range)
    val jsonString =
      """{"id": "$id", "title": "$id", "filePath": "$filePath", "range": $rangeString, "severity": "medium", "filterableIssueType": "Code Security", "isIgnored": false, "isNew": false, "additionalData": {"ruleId": "test-rule-id", "key": "test-key", "message": "Mock message", "isSecurityType": false, "rule": "mock-rule", "repoDatasetSize": 0, "exampleCommitFixes": [], "text": "", "priorityScore": 0, "hasAIFix": false, "dataFlow": [], "description": "", "language": "kotlin", "packageManager": "gradle", "packageName": "mock-package", "name": "mock-name", "version": "1.0.0", "from": [], "upgradePath": [], "isPatchable": false, "isUpgradable": false, "projectName": "test-project", "matchingIssues": [], "publicId": "test-public-id"}}"""
    val mockDiagnostic = Diagnostic()
    mockDiagnostic.range = range
    mockDiagnostic.data = jsonString
    return mockDiagnostic
  }
}
