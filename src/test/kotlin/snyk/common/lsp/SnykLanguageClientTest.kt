package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.configurationStore.StoreUtil
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
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.events.SnykFolderConfigListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykScanSummaryListener
import io.snyk.plugin.events.SnykShowIssueDetailListener
import io.snyk.plugin.events.SnykTreeViewListener
import io.snyk.plugin.getDocument
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsync
import io.snyk.plugin.refreshAnnotationsForFile
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.toVirtualFileOrNull
import io.snyk.plugin.ui.settings.HTMLSettingsPanel
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.progress.ProgressManager
import snyk.common.lsp.settings.ConfigSetting
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LsFolderSettingsKeys
import snyk.common.lsp.settings.LsSettingsKeys
import snyk.common.lsp.settings.LspConfigurationParam
import snyk.common.lsp.settings.LspFolderConfig
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
    every { projectMock.name } returns "test-project"
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
  fun `hasAuthenticated calls setAuthToken on HTMLSettingsPanel when panel is open`() {
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }

    justRun { publishAsync<Any>(any(), any(), any()) }

    val panelMock = mockk<HTMLSettingsPanel>(relaxed = true)
    HTMLSettingsPanel.instance = panelMock

    try {
      val token = "test-token-123"
      val apiUrl = "https://api.snyk.io"
      cut.hasAuthenticated(HasAuthenticatedParam(token, apiUrl))

      verify { panelMock.setAuthToken(token, apiUrl) }
    } finally {
      HTMLSettingsPanel.instance = null
    }
  }

  @Test
  fun `hasAuthenticated returns without persisting when token and api are unchanged`() {
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }

    settings.token = "unchanged-token"
    settings.customEndpointUrl = "https://unchanged.example"

    cut.hasAuthenticated(HasAuthenticatedParam("unchanged-token", "https://unchanged.example"))

    verify(exactly = 1) { lsWrapperMock.cancelPreviousLogin() }
    verify(exactly = 0) { StoreUtil.saveSettings(any(), any()) }
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
  fun `snykConfiguration should call migrateNestedFolderConfigs after adding configs`() {
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

    val lspFolderConfig =
      LspFolderConfig(
        folderPath = "/test/project",
        settings = mapOf("base_branch" to ConfigSetting(value = "main")),
      )
    val param = LspConfigurationParam(folderConfigs = listOf(lspFolderConfig))

    cut.snykConfiguration(param)

    verify(timeout = 5000) { folderConfigSettingsMock.addAll(any()) }
    verify(timeout = 5000) { folderConfigSettingsMock.migrateNestedFolderConfigs(projectMock) }
  }

  @Test
  fun `snykConfiguration with null configuration param returns immediately`() {
    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    cut.snykConfiguration(null)

    verify(exactly = 0) { folderConfigSettingsMock.addAll(any()) }
  }

  @Test
  fun `snykConfiguration does not process folder configs when language client is disposed`() {
    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    cut.dispose()

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/after/dispose",
              settings = mapOf(LsFolderSettingsKeys.BASE_BRANCH to ConfigSetting(value = "main")),
            )
          )
      )
    cut.snykConfiguration(param)

    verify(exactly = 0) { folderConfigSettingsMock.addAll(any()) }
  }

  @Test
  fun `snykConfiguration still persists folder configs when folder topic syncPublisher throws`() {
    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    every { messageBusMock.syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC) } throws
      RuntimeException("syncPublisher failure")

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test/broken-publisher",
              settings = mapOf(LsFolderSettingsKeys.BASE_BRANCH to ConfigSetting(value = "main")),
            )
          )
      )

    cut.snykConfiguration(param)

    verify(timeout = 5000) { folderConfigSettingsMock.addAll(any()) }
    verify(timeout = 5000) { folderConfigSettingsMock.migrateNestedFolderConfigs(projectMock) }
  }

  @Test
  fun `snykConfiguration with single folder and null settings does not apply folder scope to globals`() {
    settings.snykCodeSecurityIssuesScanEnable = false

    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    val folderConfigListener = mockk<SnykFolderConfigListener>(relaxed = true)
    every {
      messageBusMock.syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
    } returns folderConfigListener

    val param =
      LspConfigurationParam(
        folderConfigs = listOf(LspFolderConfig(folderPath = "/only/path", settings = null))
      )

    cut.snykConfiguration(param)

    Thread.sleep(200)

    assertFalse(settings.snykCodeSecurityIssuesScanEnable)
    verify(timeout = 2000) {
      folderConfigSettingsMock.addAll(match { it.single().settings == null })
    }
  }

  @Test
  fun `refreshCodeLenses and refreshInlineValues invoke refreshAnnotationsForOpenFiles`() {
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { projectMock.isDisposed } returns false
    justRun { refreshAnnotationsForOpenFiles(projectMock) }

    cut.refreshCodeLenses().get()
    cut.refreshInlineValues().get()

    verify(exactly = 2) { refreshAnnotationsForOpenFiles(projectMock) }
  }

  @Test
  fun `refreshCodeLenses does not call refreshAnnotationsForOpenFiles when project is disposed`() {
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { projectMock.isDisposed } returns true
    justRun { refreshAnnotationsForOpenFiles(any()) }

    cut.refreshCodeLenses().get()

    verify(exactly = 0) { refreshAnnotationsForOpenFiles(any()) }
  }

  @Test
  fun `applyEdit with null params completes without applying edits`() {
    every { projectMock.isDisposed } returns false
    val result = cut.applyEdit(null).get()
    assertTrue(result.isApplied)
    verify(exactly = 0) { snyk.common.editor.DocumentChanger.applyChange(any()) }
  }

  @Test
  fun `snykScan second InProgress for same key does not call scanningStarted twice`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val tempDir = Files.createTempDirectory("snykScanDupTest")
    val folderPath = tempDir.toAbsolutePath().toString()
    val vf = mockk<VirtualFile>(relaxed = true)
    every { folderPath.toVirtualFile() } returns vf

    ScanState.scanInProgress.clear()

    val param = SnykScanParams("inProgress", "code", folderPath)
    cut.snykScan(param)
    Thread.sleep(500)
    cut.snykScan(param)

    Thread.sleep(500)
    verify(exactly = 1) { mockListener.scanningStarted(param) }
  }

  @Test
  fun `snykScan does nothing when scan topic syncPublisher is null`() {
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns null
    cut.snykScan(SnykScanParams("success", "code", "/tmp/x"))
    Thread.sleep(200)
  }

  @Test
  fun `snykScan Success with secrets does not invoke product finished callbacks`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val tempDir = Files.createTempDirectory("snykScanSecretsSuccess")
    val folderPath = tempDir.toAbsolutePath().toString()
    val vf = mockk<VirtualFile>(relaxed = true)
    every { folderPath.toVirtualFile() } returns vf

    val param = SnykScanParams("success", "secrets", folderPath)
    cut.snykScan(param)

    Thread.sleep(500)
    verify(exactly = 0) { mockListener.scanningSnykCodeFinished() }
    verify(exactly = 0) { mockListener.scanningOssFinished() }
    verify(exactly = 0) { mockListener.scanningIacFinished() }
  }

  @Test
  fun `snykConfiguration should not run when disposed`() {
    every { projectMock.isDisposed } returns true

    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test",
              settings = mapOf("base_branch" to ConfigSetting(value = "main")),
            )
          )
      )
    cut.snykConfiguration(param)

    verify(exactly = 0) { folderConfigSettingsMock.addAll(any()) }
  }

  @Test
  fun `snykConfiguration should update plugin settings when received`() {
    // initial state
    settings.snykCodeSecurityIssuesScanEnable = false
    settings.ossScanEnable = false

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test",
              settings =
                mapOf(
                  "snyk_code_enabled" to ConfigSetting(value = true, isLocked = true),
                  "snyk_oss_enabled" to ConfigSetting(value = true, isLocked = false),
                ),
            )
          )
      )

    cut.snykConfiguration(param)

    // Give it a small amount of time to process async task
    Thread.sleep(200)

    assertTrue(settings.snykCodeSecurityIssuesScanEnable)
    assertTrue(settings.ossScanEnable)
  }

  @Test
  fun `snykConfiguration with null folderConfigs does not crash and preserves global state`() {
    settings.snykCodeSecurityIssuesScanEnable = true
    settings.ossScanEnable = false
    settings.iacScanEnabled = true
    settings.secretsEnabled = false
    settings.criticalSeverityEnabled = true
    settings.highSeverityEnabled = false

    val param = LspConfigurationParam(folderConfigs = null)

    cut.snykConfiguration(param)

    Thread.sleep(200)

    assertTrue(settings.snykCodeSecurityIssuesScanEnable)
    assertFalse(settings.ossScanEnable)
    assertTrue(settings.iacScanEnabled)
    assertFalse(settings.secretsEnabled)
    assertTrue(settings.criticalSeverityEnabled)
    assertFalse(settings.highSeverityEnabled)
  }

  @Test
  fun `snykConfiguration with empty folderConfigs does not crash and preserves global state`() {
    settings.snykCodeSecurityIssuesScanEnable = true
    settings.ossScanEnable = false
    settings.iacScanEnabled = true
    settings.secretsEnabled = false
    settings.criticalSeverityEnabled = true
    settings.highSeverityEnabled = false

    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    val folderConfigListener = mockk<SnykFolderConfigListener>(relaxed = true)
    every {
      messageBusMock.syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
    } returns folderConfigListener

    val param = LspConfigurationParam(folderConfigs = emptyList())

    cut.snykConfiguration(param)

    Thread.sleep(200)

    assertTrue(settings.snykCodeSecurityIssuesScanEnable)
    assertFalse(settings.ossScanEnable)
    assertTrue(settings.iacScanEnabled)
    assertFalse(settings.secretsEnabled)
    assertTrue(settings.criticalSeverityEnabled)
    assertFalse(settings.highSeverityEnabled)
  }

  @Test
  fun `snykConfiguration with single folder config maps toggles to global state`() {
    settings.snykCodeSecurityIssuesScanEnable = false
    settings.ossScanEnable = false
    settings.iacScanEnabled = false
    settings.secretsEnabled = false
    settings.criticalSeverityEnabled = false
    settings.highSeverityEnabled = false
    settings.mediumSeverityEnabled = false
    settings.lowSeverityEnabled = false

    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    val folderConfigListener = mockk<SnykFolderConfigListener>(relaxed = true)
    every {
      messageBusMock.syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
    } returns folderConfigListener

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns settings
    justRun { publishAsync<Any>(any(), any(), any()) }

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test/project",
              settings =
                mapOf(
                  LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SNYK_OSS_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SNYK_IAC_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SNYK_SECRETS_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SEVERITY_FILTER_HIGH to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SEVERITY_FILTER_LOW to ConfigSetting(value = true),
                ),
            )
          )
      )

    cut.snykConfiguration(param)

    Thread.sleep(500)

    assertTrue("Code should be enabled", settings.snykCodeSecurityIssuesScanEnable)
    assertTrue("OSS should be enabled", settings.ossScanEnable)
    assertTrue("IaC should be enabled", settings.iacScanEnabled)
    assertTrue("Secrets should be enabled", settings.secretsEnabled)
    // Severity filters are NOT mirrored to global state — they live exclusively in folder configs
    assertFalse("Critical severity should remain unchanged", settings.criticalSeverityEnabled)
    assertFalse("High severity should remain unchanged", settings.highSeverityEnabled)
    assertFalse("Medium severity should remain unchanged", settings.mediumSeverityEnabled)
    assertFalse("Low severity should remain unchanged", settings.lowSeverityEnabled)
  }

  @Test
  fun `snykConfiguration with multiple folder configs does not toggle global plugin state`() {
    settings.snykCodeSecurityIssuesScanEnable = false
    settings.ossScanEnable = false
    settings.iacScanEnabled = false
    settings.secretsEnabled = false
    settings.criticalSeverityEnabled = false
    settings.highSeverityEnabled = false
    settings.mediumSeverityEnabled = false
    settings.lowSeverityEnabled = false

    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    val folderConfigListener = mockk<SnykFolderConfigListener>(relaxed = true)
    every {
      messageBusMock.syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
    } returns folderConfigListener

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns settings
    justRun { publishAsync<Any>(any(), any(), any()) }

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test/project1",
              settings =
                mapOf(
                  LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SNYK_OSS_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SNYK_IAC_ENABLED to ConfigSetting(value = false),
                  LsFolderSettingsKeys.SNYK_SECRETS_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SEVERITY_FILTER_HIGH to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SEVERITY_FILTER_LOW to ConfigSetting(value = true),
                ),
            ),
            LspFolderConfig(
              folderPath = "/test/project2",
              settings =
                mapOf(
                  LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = false),
                  LsFolderSettingsKeys.SNYK_OSS_ENABLED to ConfigSetting(value = false),
                  LsFolderSettingsKeys.SNYK_IAC_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SNYK_SECRETS_ENABLED to ConfigSetting(value = false),
                ),
            ),
          )
      )

    cut.snykConfiguration(param)

    Thread.sleep(500)

    assertFalse(
      "Global code toggle must not change with multiple folder configs",
      settings.snykCodeSecurityIssuesScanEnable,
    )
    assertFalse("Global OSS toggle must not change", settings.ossScanEnable)
    assertFalse("Global IaC toggle must not change", settings.iacScanEnabled)
    assertFalse("Global secrets toggle must not change", settings.secretsEnabled)
    assertFalse(settings.criticalSeverityEnabled)
    assertFalse(settings.highSeverityEnabled)
    assertFalse(settings.mediumSeverityEnabled)
    assertFalse(settings.lowSeverityEnabled)

    verify(timeout = 5000) { folderConfigSettingsMock.addAll(any()) }
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

  @Test
  fun `snykConfiguration stores autoDeterminedOrg in FolderConfigSettings`() {
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

    val lspFolderConfig =
      LspFolderConfig(
        folderPath = "/test/project",
        settings =
          mapOf(
            LsFolderSettingsKeys.AUTO_DETERMINED_ORG to ConfigSetting(value = "auto-org"),
            LsFolderSettingsKeys.ORG_SET_BY_USER to ConfigSetting(value = false),
            LsFolderSettingsKeys.PREFERRED_ORG to ConfigSetting(value = ""),
          ),
      )
    val param = LspConfigurationParam(folderConfigs = listOf(lspFolderConfig))

    cut.snykConfiguration(param)

    val capturedConfigs = slot<List<LspFolderConfig>>()
    verify(timeout = 5000) { folderConfigSettingsMock.addAll(capture(capturedConfigs)) }

    val storedConfig = capturedConfigs.captured.first()
    assertEquals(
      "autoDeterminedOrg should be passed to addAll",
      "auto-org",
      storedConfig.settings?.get(LsFolderSettingsKeys.AUTO_DETERMINED_ORG)?.value,
    )
    assertEquals(
      "orgSetByUser should be passed to addAll",
      false,
      storedConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value,
    )
    assertEquals(
      "preferredOrg should be passed to addAll",
      "",
      storedConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
  }

  @Test
  fun `snykConfiguration stores per-folder product toggles in FolderConfigSettings`() {
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

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }
    justRun { publishAsync<Any>(any(), any(), any()) }

    val lspFolderConfig =
      LspFolderConfig(
        folderPath = "/test/project",
        settings =
          mapOf(
            LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = true),
            LsFolderSettingsKeys.SNYK_OSS_ENABLED to ConfigSetting(value = false),
            LsFolderSettingsKeys.SNYK_IAC_ENABLED to ConfigSetting(value = true),
            LsFolderSettingsKeys.SNYK_SECRETS_ENABLED to ConfigSetting(value = false),
          ),
      )
    val param = LspConfigurationParam(folderConfigs = listOf(lspFolderConfig))

    cut.snykConfiguration(param)

    val capturedConfigs = slot<List<LspFolderConfig>>()
    verify(timeout = 5000) { folderConfigSettingsMock.addAll(capture(capturedConfigs)) }

    val storedConfig = capturedConfigs.captured.first()
    assertEquals(
      "snyk_code_enabled should be stored as true",
      true,
      storedConfig.settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)?.value,
    )
    assertEquals(
      "snyk_oss_enabled should be stored as false",
      false,
      storedConfig.settings?.get(LsFolderSettingsKeys.SNYK_OSS_ENABLED)?.value,
    )
    assertEquals(
      "snyk_iac_enabled should be stored as true",
      true,
      storedConfig.settings?.get(LsFolderSettingsKeys.SNYK_IAC_ENABLED)?.value,
    )
    assertEquals(
      "snyk_secrets_enabled should be stored as false",
      false,
      storedConfig.settings?.get(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED)?.value,
    )
  }

  @Test
  fun `applyFolderScopeSettingsToPluginState maps asymmetric toggles to global state`() {
    settings.snykCodeSecurityIssuesScanEnable = true
    settings.ossScanEnable = false
    settings.iacScanEnabled = true
    settings.secretsEnabled = false

    val folderConfigSettingsMock = mockk<FolderConfigSettings>(relaxed = true)
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock

    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    val folderConfigListener = mockk<SnykFolderConfigListener>(relaxed = true)
    every {
      messageBusMock.syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
    } returns folderConfigListener

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns settings
    justRun { publishAsync<Any>(any(), any(), any()) }

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test/project",
              settings =
                mapOf(
                  LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = false),
                  LsFolderSettingsKeys.SNYK_OSS_ENABLED to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SNYK_IAC_ENABLED to ConfigSetting(value = false),
                  LsFolderSettingsKeys.SNYK_SECRETS_ENABLED to ConfigSetting(value = true),
                ),
            )
          )
      )

    cut.snykConfiguration(param)

    Thread.sleep(500)

    assertFalse(
      "Code should be disabled (from folder config)",
      settings.snykCodeSecurityIssuesScanEnable,
    )
    assertTrue("OSS should be enabled (from folder config)", settings.ossScanEnable)
    assertFalse("IaC should be disabled (from folder config)", settings.iacScanEnabled)
    assertTrue("Secrets should be enabled (from folder config)", settings.secretsEnabled)
  }

  @Test
  fun `product toggle round-trip preserves values through full cycle`() {
    settings.snykCodeSecurityIssuesScanEnable = false
    settings.ossScanEnable = false
    settings.iacScanEnabled = false
    settings.secretsEnabled = false

    // Use real FolderConfigSettings for round-trip verification
    val realFolderConfigSettings = FolderConfigSettings()
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      realFolderConfigSettings

    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock

    val folderConfigListener = mockk<SnykFolderConfigListener>(relaxed = true)
    every {
      messageBusMock.syncPublisher(SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
    } returns folderConfigListener

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns settings
    justRun { publishAsync<Any>(any(), any(), any()) }

    val inputCodeEnabled = true
    val inputOssEnabled = false
    val inputIacEnabled = true
    val inputSecretsEnabled = false

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test/roundtrip",
              settings =
                mapOf(
                  LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = inputCodeEnabled),
                  LsFolderSettingsKeys.SNYK_OSS_ENABLED to ConfigSetting(value = inputOssEnabled),
                  LsFolderSettingsKeys.SNYK_IAC_ENABLED to ConfigSetting(value = inputIacEnabled),
                  LsFolderSettingsKeys.SNYK_SECRETS_ENABLED to
                    ConfigSetting(value = inputSecretsEnabled),
                ),
            )
          )
      )

    cut.snykConfiguration(param)

    Thread.sleep(500)

    // Verify storage: retrieve from FolderConfigSettings directly
    val normalizedPath =
      java.nio.file.Paths.get("/test/roundtrip").normalize().toAbsolutePath().toString()
    val storedConfig = realFolderConfigSettings.getFolderConfig(normalizedPath)

    assertEquals(
      "Stored code toggle should match input",
      inputCodeEnabled,
      storedConfig.settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)?.value,
    )
    assertEquals(
      "Stored oss toggle should match input",
      inputOssEnabled,
      storedConfig.settings?.get(LsFolderSettingsKeys.SNYK_OSS_ENABLED)?.value,
    )
    assertEquals(
      "Stored iac toggle should match input",
      inputIacEnabled,
      storedConfig.settings?.get(LsFolderSettingsKeys.SNYK_IAC_ENABLED)?.value,
    )
    assertEquals(
      "Stored secrets toggle should match input",
      inputSecretsEnabled,
      storedConfig.settings?.get(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED)?.value,
    )

    // Verify global state mapping (from first folder)
    assertEquals(
      "Global code setting should match first folder",
      inputCodeEnabled,
      settings.snykCodeSecurityIssuesScanEnable,
    )
    assertEquals(
      "Global oss setting should match first folder",
      inputOssEnabled,
      settings.ossScanEnable,
    )
    assertEquals(
      "Global iac setting should match first folder",
      inputIacEnabled,
      settings.iacScanEnabled,
    )
    assertEquals(
      "Global secrets setting should match first folder",
      inputSecretsEnabled,
      settings.secretsEnabled,
    )
  }

  @Test
  fun `snykConfiguration reads API_ENDPOINT from LS payload`() {
    settings.customEndpointUrl = ""

    val param =
      LspConfigurationParam(
        settings =
          mapOf(LsSettingsKeys.API_ENDPOINT to ConfigSetting(value = "https://api.eu.snyk.io"))
      )

    cut.snykConfiguration(param)

    Thread.sleep(500)

    assertEquals("https://api.eu.snyk.io", settings.customEndpointUrl)
  }

  @Test
  fun `snykConfiguration reads all round-trip machine-scope keys`() {
    settings.customEndpointUrl = ""
    settings.ignoreUnknownCA = false
    settings.organization = ""
    settings.manageBinariesAutomatically = true
    settings.cliPath = ""
    settings.cliBaseDownloadURL = ""
    settings.cliReleaseChannel = "stable"

    val param =
      LspConfigurationParam(
        settings =
          mapOf(
            LsSettingsKeys.API_ENDPOINT to ConfigSetting(value = "https://api.eu.snyk.io"),
            LsSettingsKeys.PROXY_INSECURE to ConfigSetting(value = true),
            LsSettingsKeys.ORGANIZATION to ConfigSetting(value = "my-org"),
            LsSettingsKeys.AUTOMATIC_DOWNLOAD to ConfigSetting(value = false),
            LsSettingsKeys.CLI_PATH to ConfigSetting(value = "/opt/snyk/cli"),
            LsSettingsKeys.BINARY_BASE_URL to ConfigSetting(value = "https://static.snyk.io"),
            LsSettingsKeys.CLI_RELEASE_CHANNEL to ConfigSetting(value = "preview"),
          )
      )

    cut.snykConfiguration(param)

    Thread.sleep(500)

    assertEquals("https://api.eu.snyk.io", settings.customEndpointUrl)
    assertEquals(true, settings.ignoreUnknownCA)
    assertEquals("my-org", settings.organization)
    assertEquals(false, settings.manageBinariesAutomatically)
    assertEquals("/opt/snyk/cli", settings.cliPath)
    assertEquals("https://static.snyk.io", settings.cliBaseDownloadURL)
    assertEquals("preview", settings.cliReleaseChannel)
  }

  @Test
  fun `machine-scope round-trip preserves all values`() {
    // Simulate the outgoing payload that getSettings() would produce
    // (getSettings serializes each setting as a ConfigSetting with the value from plugin state)
    val outgoingSettings =
      mapOf(
        LsSettingsKeys.API_ENDPOINT to ConfigSetting(value = "https://api.eu.snyk.io"),
        LsSettingsKeys.PROXY_INSECURE to ConfigSetting(value = true),
        LsSettingsKeys.ORGANIZATION to ConfigSetting(value = "round-trip-org"),
        LsSettingsKeys.AUTOMATIC_DOWNLOAD to ConfigSetting(value = false),
        LsSettingsKeys.CLI_PATH to ConfigSetting(value = "/usr/local/bin/snyk"),
        LsSettingsKeys.BINARY_BASE_URL to ConfigSetting(value = "https://static.snyk.io"),
        LsSettingsKeys.CLI_RELEASE_CHANNEL to ConfigSetting(value = "preview"),
      )

    // Reset settings to different values to prove snykConfiguration writes them back
    settings.customEndpointUrl = "https://changed.example.com"
    settings.ignoreUnknownCA = false
    settings.organization = "changed-org"
    settings.manageBinariesAutomatically = true
    settings.cliPath = "/changed/path"
    settings.cliBaseDownloadURL = "https://changed.example.com"
    settings.cliReleaseChannel = "stable"

    // Feed the outgoing settings back as an incoming LS payload
    val incomingParam = LspConfigurationParam(settings = outgoingSettings)
    cut.snykConfiguration(incomingParam)

    Thread.sleep(500)

    // Verify the round-tripped values match the originals
    assertEquals("https://api.eu.snyk.io", settings.customEndpointUrl)
    assertEquals(true, settings.ignoreUnknownCA)
    assertEquals("round-trip-org", settings.organization)
    assertEquals(false, settings.manageBinariesAutomatically)
    assertEquals("/usr/local/bin/snyk", settings.cliPath)
    assertEquals("https://static.snyk.io", settings.cliBaseDownloadURL)
    assertEquals("preview", settings.cliReleaseChannel)
  }

  @Test
  fun `telemetryEvent does nothing`() {
    cut.telemetryEvent(null)
    cut.telemetryEvent("some event")
  }

  @Test
  fun `notifyProgress delegates to progressManager`() {
    val params = mockk<ProgressParams>(relaxed = true)
    cut.notifyProgress(params)
  }

  @Test
  fun `publishDiagnostics316 does nothing`() {
    cut.publishDiagnostics316(null)
    cut.publishDiagnostics316(mockk(relaxed = true))
  }

  @Test
  fun `publishDiagnostics returns early on null param`() {
    cut.publishDiagnostics(null)
  }

  @Test
  fun `refreshInlineValues returns completed future`() {
    val result = cut.refreshInlineValues()
    assertNotNull(result)
  }

  @Test
  fun `logMessage logs different message types`() {
    // Note: MessageType.Error is skipped because IntelliJ DefaultLogger throws AssertionError
    cut.logMessage(org.eclipse.lsp4j.MessageParams(MessageType.Warning, "warn msg"))
    cut.logMessage(org.eclipse.lsp4j.MessageParams(MessageType.Info, "info msg"))
    cut.logMessage(org.eclipse.lsp4j.MessageParams(MessageType.Log, "log msg"))
    // null MessageParams is handled gracefully
    cut.logMessage(null)
  }

  @Test
  fun `logTrace does nothing when not disposed`() {
    cut.logTrace(org.eclipse.lsp4j.LogTraceParams("trace message"))
    cut.logTrace(null)
  }

  @Test
  fun `logTrace does not run when disposed`() {
    every { projectMock.isDisposed } returns true
    cut.logTrace(org.eclipse.lsp4j.LogTraceParams("trace message"))
  }

  @Test
  fun `dispose sets disposed flag`() {
    assertFalse(cut.isDisposed())
    cut.dispose()
    assertTrue(cut.isDisposed())
  }

  @Test
  fun `applyEdit returns false future when disposed`() {
    every { projectMock.isDisposed } returns true
    val result = cut.applyEdit(null)
    assertFalse(result.get().isApplied)
  }

  @Test
  fun `refreshCodeLenses returns completed future when not disposed`() {
    val result = cut.refreshCodeLenses()
    assertNotNull(result)
  }

  @Test
  fun `showDocument returns false when disposed`() {
    every { projectMock.isDisposed } returns true
    val result = cut.showDocument(ShowDocumentParams("file:///test.txt"))
    assertFalse(result.get().isSuccess)
  }

  @Test
  fun `snykScanSummary does not run when disposed`() {
    every { projectMock.isDisposed } returns true
    cut.snykScanSummary(mockk(relaxed = true))
  }

  @Test
  fun `snykScanSummary publishes when client is not disposed`() {
    every { projectMock.isDisposed } returns false
    mockkStatic("io.snyk.plugin.UtilsKt")
    justRun { publishAsync<SnykScanSummaryListener>(any(), any(), any()) }

    val params = mockk<SnykScanSummaryParams>(relaxed = true)
    cut.snykScanSummary(params)

    verify {
      publishAsync<SnykScanSummaryListener>(
        projectMock,
        SnykScanSummaryListener.SNYK_SCAN_SUMMARY_TOPIC,
        any(),
      )
    }
  }

  @Test
  fun `publishDiagnostics with empty diagnostics and OpenSource version`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val vf = mockk<VirtualFile>(relaxed = true)
    val fileUri = "file:///tmp/test-oss.kt"
    every { fileUri.toVirtualFileOrNull() } returns vf

    val diagnosticsParams = PublishDiagnosticsParams(fileUri, emptyList())
    diagnosticsParams.version = LsProduct.OpenSource.ordinal

    cut.publishDiagnostics(diagnosticsParams)

    Thread.sleep(200)
    verify { mockListener.onPublishDiagnostics(LsProduct.OpenSource, any(), emptySet()) }
  }

  @Test
  fun `publishDiagnostics with empty diagnostics and Code version`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val vf = mockk<VirtualFile>(relaxed = true)
    val fileUri = "file:///tmp/test-code.kt"
    every { fileUri.toVirtualFileOrNull() } returns vf

    val diagnosticsParams = PublishDiagnosticsParams(fileUri, emptyList())
    diagnosticsParams.version = LsProduct.Code.ordinal

    cut.publishDiagnostics(diagnosticsParams)

    Thread.sleep(200)
    verify { mockListener.onPublishDiagnostics(LsProduct.Code, any(), emptySet()) }
  }

  @Test
  fun `publishDiagnostics with empty diagnostics and IaC version`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val vf = mockk<VirtualFile>(relaxed = true)
    val fileUri = "file:///tmp/test-iac.kt"
    every { fileUri.toVirtualFileOrNull() } returns vf

    val diagnosticsParams = PublishDiagnosticsParams(fileUri, emptyList())
    diagnosticsParams.version = LsProduct.InfrastructureAsCode.ordinal

    cut.publishDiagnostics(diagnosticsParams)

    Thread.sleep(200)
    verify { mockListener.onPublishDiagnostics(LsProduct.InfrastructureAsCode, any(), emptySet()) }
  }

  @Test
  fun `publishDiagnostics with empty diagnostics and Secrets version`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val vf = mockk<VirtualFile>(relaxed = true)
    val fileUri = "file:///tmp/test-secrets.kt"
    every { fileUri.toVirtualFileOrNull() } returns vf

    val diagnosticsParams = PublishDiagnosticsParams(fileUri, emptyList())
    diagnosticsParams.version = LsProduct.Secrets.ordinal

    cut.publishDiagnostics(diagnosticsParams)

    Thread.sleep(200)
    verify { mockListener.onPublishDiagnostics(LsProduct.Secrets, any(), emptySet()) }
  }

  @Test
  fun `publishDiagnostics with empty diagnostics and unknown version clears all products`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val vf = mockk<VirtualFile>(relaxed = true)
    val fileUri = "file:///tmp/test-unknown.kt"
    every { fileUri.toVirtualFileOrNull() } returns vf

    val diagnosticsParams = PublishDiagnosticsParams(fileUri, emptyList())
    diagnosticsParams.version = 999

    cut.publishDiagnostics(diagnosticsParams)

    Thread.sleep(200)
    verify { mockListener.onPublishDiagnostics(LsProduct.Code, any(), emptySet()) }
    verify { mockListener.onPublishDiagnostics(LsProduct.OpenSource, any(), emptySet()) }
    verify { mockListener.onPublishDiagnostics(LsProduct.InfrastructureAsCode, any(), emptySet()) }
    verify { mockListener.onPublishDiagnostics(LsProduct.Secrets, any(), emptySet()) }
  }

  @Test
  fun `snykConfiguration reads risk score threshold from folder config`() {
    settings.riskScoreThreshold = null

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

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }
    justRun { publishAsync<Any>(any(), any(), any()) }

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test/project",
              settings =
                mapOf(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD to ConfigSetting(value = 700.0)),
            )
          )
      )

    cut.snykConfiguration(param)

    Thread.sleep(500)

    assertEquals(700, settings.riskScoreThreshold)
  }

  @Test
  fun `snykConfiguration reads issue view options from folder config`() {
    settings.openIssuesEnabled = true
    settings.ignoredIssuesEnabled = false

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

    mockkStatic(StoreUtil::class)
    justRun { StoreUtil.saveSettings(any(), any()) }
    justRun { publishAsync<Any>(any(), any(), any()) }

    val param =
      LspConfigurationParam(
        folderConfigs =
          listOf(
            LspFolderConfig(
              folderPath = "/test/project",
              settings =
                mapOf(
                  LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES to ConfigSetting(value = false),
                  LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES to ConfigSetting(value = true),
                  LsFolderSettingsKeys.SCAN_AUTOMATIC to ConfigSetting(value = false),
                  LsFolderSettingsKeys.SCAN_NET_NEW to ConfigSetting(value = true),
                ),
            )
          )
      )

    cut.snykConfiguration(param)

    Thread.sleep(500)

    assertFalse(settings.openIssuesEnabled)
    assertTrue(settings.ignoredIssuesEnabled)
    assertFalse(settings.scanOnSave)
    assertTrue(settings.isDeltaFindingsEnabled())
  }

  @Test
  fun `showDocument returns false when document is null for file with selection`() {
    val fileUri = "file:///tmp/test-no-doc.kt"
    val virtualFile = mockk<VirtualFile>(relaxed = true)

    every { fileUri.toVirtualFileOrNull() } returns virtualFile
    every { virtualFile.isValid } returns true
    every { virtualFile.getDocument() } returns null

    val params =
      ShowDocumentParams(fileUri).apply { selection = Range(Position(5, 0), Position(5, 10)) }

    val result = cut.showDocument(params).get()

    assertFalse(result.isSuccess)
  }

  @Test
  fun `showDocument with Snyk OSS product URI`() {
    val mockListener = mockk<SnykShowIssueDetailListener>(relaxed = true)
    val latch = CountDownLatch(1)
    every { mockListener.onShowIssueDetail(any()) } answers { latch.countDown() }
    every {
      messageBusMock.syncPublisher(SnykShowIssueDetailListener.SHOW_ISSUE_DETAIL_TOPIC)
    } returns mockListener

    val url =
      "snyk:///temp/test.txt?product=Snyk+Open+Source&issueId=12345&action=showInDetailPanel"
    val result = cut.showDocument(ShowDocumentParams(url)).get()

    assertTrue(result.isSuccess)
    assertTrue("Async publish should complete within timeout", latch.await(2, TimeUnit.SECONDS))
    verify { mockListener.onShowIssueDetail(any()) }
  }

  @Test
  fun `showDocument with Snyk Secrets product URI`() {
    val mockListener = mockk<SnykShowIssueDetailListener>(relaxed = true)
    val latch = CountDownLatch(1)
    every { mockListener.onShowIssueDetail(any()) } answers { latch.countDown() }
    every {
      messageBusMock.syncPublisher(SnykShowIssueDetailListener.SHOW_ISSUE_DETAIL_TOPIC)
    } returns mockListener

    val url = "snyk:///temp/test.txt?product=Snyk+Secrets&issueId=12345&action=showInDetailPanel"
    val result = cut.showDocument(ShowDocumentParams(url)).get()

    assertTrue(result.isSuccess)
    assertTrue("Async publish should complete within timeout", latch.await(2, TimeUnit.SECONDS))
    verify { mockListener.onShowIssueDetail(any()) }
  }

  @Test
  fun `showDocument with unknown Snyk product URI`() {
    val url = "snyk:///temp/test.txt?product=Snyk+Unknown&issueId=12345&action=showInDetailPanel"
    val result = cut.showDocument(ShowDocumentParams(url)).get()

    assertFalse(result.isSuccess)
  }

  @Test
  fun `snykScan with InProgress status triggers scanningStarted`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val tempDir = Files.createTempDirectory("snykScanTest")
    val folderPath = tempDir.toAbsolutePath().toString()
    val vf = mockk<VirtualFile>(relaxed = true)
    every { folderPath.toVirtualFile() } returns vf

    ScanState.scanInProgress.clear()

    val param = SnykScanParams("inProgress", "code", folderPath)
    cut.snykScan(param)

    Thread.sleep(500)
    verify { mockListener.scanningStarted(param) }
  }

  @Test
  fun `snykScan with Success status triggers scanningSnykCodeFinished`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val tempDir = Files.createTempDirectory("snykScanSuccessTest")
    val folderPath = tempDir.toAbsolutePath().toString()
    val vf = mockk<VirtualFile>(relaxed = true)
    every { folderPath.toVirtualFile() } returns vf

    val param = SnykScanParams("success", "code", folderPath)
    cut.snykScan(param)

    Thread.sleep(500)
    verify { mockListener.scanningSnykCodeFinished() }
  }

  @Test
  fun `snykScan with Error status triggers scanningError`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val tempDir = Files.createTempDirectory("snykScanErrorTest")
    val folderPath = tempDir.toAbsolutePath().toString()
    val vf = mockk<VirtualFile>(relaxed = true)
    every { folderPath.toVirtualFile() } returns vf

    val param = SnykScanParams("error", "code", folderPath)
    cut.snykScan(param)

    Thread.sleep(500)
    verify { mockListener.scanningError(param) }
  }

  @Test
  fun `snykScan with OSS Success triggers scanningOssFinished`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val tempDir = Files.createTempDirectory("snykScanOssTest")
    val folderPath = tempDir.toAbsolutePath().toString()
    val vf = mockk<VirtualFile>(relaxed = true)
    every { folderPath.toVirtualFile() } returns vf

    val param = SnykScanParams("success", "oss", folderPath)
    cut.snykScan(param)

    Thread.sleep(500)
    verify { mockListener.scanningOssFinished() }
  }

  @Test
  fun `snykScan with IaC Success triggers scanningIacFinished`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val tempDir = Files.createTempDirectory("snykScanIacTest")
    val folderPath = tempDir.toAbsolutePath().toString()
    val vf = mockk<VirtualFile>(relaxed = true)
    every { folderPath.toVirtualFile() } returns vf

    val param = SnykScanParams("success", "iac", folderPath)
    cut.snykScan(param)

    Thread.sleep(500)
    verify { mockListener.scanningIacFinished() }
  }

  @Test
  fun `publishDiagnostics with non-empty diagnostics publishes issues for product`() {
    val mockListener = mockk<SnykScanListener>(relaxed = true)
    every { messageBusMock.syncPublisher(SnykScanListener.SNYK_SCAN_TOPIC) } returns mockListener

    val tempFile = Files.createTempFile("testPublishDiag", ".java")
    val filePath = tempFile.fileName.toString()
    val vf = mockk<VirtualFile>(relaxed = true)
    val fileUri = tempFile.toUri().toString()
    every { fileUri.toVirtualFileOrNull() } returns vf
    every { filePath.toVirtualFile() } returns vf
    val document = mockk<Document>(relaxed = true)
    every { vf.getDocument() } returns document
    every { document.getLineStartOffset(any()) } returns 0

    val range = Range(Position(0, 0), Position(0, 1))
    val diagnostic = createMockDiagnostic(range, "Issue1", filePath)
    diagnostic.source = "Snyk Code"
    val diagnosticsParams = PublishDiagnosticsParams(fileUri, listOf(diagnostic))

    cut.publishDiagnostics(diagnosticsParams)

    Thread.sleep(500)
    verify { mockListener.onPublishDiagnostics(LsProduct.Code, any(), match { it.size == 1 }) }
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
