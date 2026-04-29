package snyk.common.lsp

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CompletableFuture
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import org.apache.commons.lang3.SystemUtils
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.analytics.ScanDoneEvent
import snyk.common.lsp.commands.COMMAND_CODE_FIX_APPLY_AI_EDIT
import snyk.common.lsp.commands.COMMAND_WORKSPACE_CONFIGURATION
import snyk.common.lsp.settings.ConfigSetting
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LsFolderSettingsKeys
import snyk.common.lsp.settings.LsSettingsKeys
import snyk.common.lsp.settings.LspFolderConfig
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService

class LanguageServerWrapperTest {
  private val folderConfigSettingsMock: FolderConfigSettings = mockk(relaxed = true)
  private val applicationMock: Application = mockk()
  private val projectMock: Project = mockk()
  private val lsMock: LanguageServer = mockk()
  private val settings = SnykApplicationSettingsStateService()
  private val trustServiceMock = mockk<WorkspaceTrustService>(relaxed = true)
  private val dumbServiceMock = mockk<DumbService>()

  private lateinit var cut: LanguageServerWrapper
  private val snykPluginDisposable = mockk<SnykPluginDisposable>(relaxed = true)

  @Before
  fun setUp() {
    unmockkAll()
    mockkStatic("io.snyk.plugin.UtilsKt")
    mockkStatic(ApplicationManager::class)

    every { ApplicationManager.getApplication() } returns applicationMock
    every { applicationMock.getService(WorkspaceTrustService::class.java) } returns trustServiceMock

    val projectManagerMock = mockk<ProjectManager>()
    every { applicationMock.getService(ProjectManager::class.java) } returns projectManagerMock
    every { applicationMock.getService(SnykPluginDisposable::class.java) } returns
      snykPluginDisposable
    every { applicationMock.getService(FolderConfigSettings::class.java) } returns
      folderConfigSettingsMock
    every { applicationMock.isDisposed } returns false

    every { projectManagerMock.openProjects } returns arrayOf(projectMock)
    every { projectMock.isDisposed } returns false
    every { projectMock.getService(DumbService::class.java) } returns dumbServiceMock
    every { projectMock.getService(SnykPluginDisposable::class.java) } returns snykPluginDisposable
    every { dumbServiceMock.isDumb } returns false

    every { pluginSettings() } returns settings
    mockkStatic("snyk.PluginInformationKt")
    every { pluginInfo } returns mockk(relaxed = true)
    every { pluginInfo.integrationName } returns "Snyk Intellij Plugin"
    every { pluginInfo.integrationVersion } returns "2.4.61"
    every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
    every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"

    cut = LanguageServerWrapper(projectMock)
    cut.languageServer = lsMock
    cut.isInitialized = true
    settings.token = "testToken"
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `sendInitializeMessage should send an initialize message to the language server`() {
    val rootManagerMock = mockk<ProjectRootManager>(relaxed = true)
    every { projectMock.getService(ProjectRootManager::class.java) } returns rootManagerMock
    every { projectMock.isDisposed } returns false
    every { rootManagerMock.contentRoots } returns emptyArray()
    every { projectMock.basePath } returns null
    every { lsMock.initialize(any<InitializeParams>()) } returns
      CompletableFuture.completedFuture(null)
    justRun { lsMock.initialized(any()) }

    cut.sendInitializeMessage()

    verify { lsMock.initialize(any<InitializeParams>()) }
    verify { lsMock.initialized(any()) }
  }

  @Test
  fun `sendInitializeMessage should not send an initialize message to the language server if disposed`() {
    every { applicationMock.isDisposed } returns true

    cut.sendInitializeMessage()

    verify(exactly = 0) { lsMock.initialize(any<InitializeParams>()) }
    verify(exactly = 0) { lsMock.initialized(any()) }
  }

  @Test
  fun `sendReportAnalyticsCommand should send a reportAnalytics command to the language server`() {
    simulateRunningLS()

    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(null)

    cut.sendReportAnalyticsCommand(mockk(relaxed = true))

    verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
  }

  @Test
  fun `sendReportAnalyticsCommand should not send a reportAnalytics command to the language server if disposed`() {
    simulateRunningLS()
    every { applicationMock.isDisposed } returns true

    cut.sendReportAnalyticsCommand(
      ScanDoneEvent(
        ScanDoneEvent.Data(
          attributes =
            ScanDoneEvent.Attributes(
              durationMs = 1000.toString(),
              path = "testPath",
              scanType = "testScan",
              uniqueIssueCount = ScanDoneEvent.UniqueIssueCount(0, 0, 0, 0),
            )
        )
      )
    )

    verify(exactly = 0) { lsMock.workspaceService.executeCommand(any()) }
  }

  @Test
  fun `ensureLanguageServerInitialized returns true when initialized`() {
    simulateRunningLS()

    val initialized = cut.ensureLanguageServerInitialized()

    assertTrue(initialized)
  }

  @Test
  fun `ensureLanguageServerInitialized returns false when disposed`() {
    every { applicationMock.isDisposed } returns true

    val initialized = cut.ensureLanguageServerInitialized()

    assertFalse(initialized)
  }

  @Test
  fun `ensureLanguageServerInitialized prevents initializing twice`() {
    cut.isInitializing.lock()
    try {
      cut.ensureLanguageServerInitialized()
      fail("expected assertion error")
    } catch (_: AssertionError) {
      // expected
    }
  }

  @Test
  fun `sendScanCommand waits for smart mode`() {
    simulateRunningLS()
    justRun { dumbServiceMock.runWhenSmart(any()) }

    cut.sendScanCommand()

    verify { dumbServiceMock.runWhenSmart(any()) }
  }

  @Test
  fun `sendScanCommand only runs when initialized`() {
    cut.isInitialized = false
    cut.sendScanCommand()

    verify(exactly = 0) { dumbServiceMock.runWhenSmart(any()) }
  }

  @Test
  fun `sendScanCommand only runs when not disposed`() {
    every { applicationMock.isDisposed } returns true

    cut.sendScanCommand()

    verify(exactly = 0) { dumbServiceMock.runWhenSmart(any()) }
  }

  @Test
  fun `sendFolderScanCommand only runs when not disposed`() {
    every { applicationMock.isDisposed } returns true

    cut.sendFolderScanCommand("testFolder", projectMock)

    verify(exactly = 0) { lsMock.workspaceService.executeCommand(any()) }
  }

  @Test
  fun `sendFolderScanCommand triggers LS when run and in smart mode`() {
    simulateRunningLS()

    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(null)

    val folder = "testFolder"
    cut.configuredWorkspaceFolders.add(
      WorkspaceFolder(Paths.get(folder).toUri().toASCIIString(), folder)
    )

    cut.sendFolderScanCommand(folder, projectMock)

    verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
  }

  @Test
  fun `sendFolderScanCommand does not trigger LS when run and in dumb mode`() {
    simulateRunningLS()
    every { dumbServiceMock.isDumb } returns true

    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(null)

    cut.sendFolderScanCommand("testFolder", projectMock)

    verify(exactly = 0) { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
  }

  @Test
  fun `updateConfiguration should not run when LS not initialized`() {
    cut.isInitialized = false
    cut.updateConfiguration()

    verify(exactly = 0) {
      lsMock.workspaceService.didChangeConfiguration(any<DidChangeConfigurationParams>())
    }
  }

  @Test
  fun `updateConfiguration should not run when disposed`() {
    every { applicationMock.isDisposed } returns true
    simulateRunningLS()

    cut.updateConfiguration()

    verify(exactly = 0) {
      lsMock.workspaceService.didChangeConfiguration(any<DidChangeConfigurationParams>())
    }
  }

  @Test
  fun `updateConfiguration should run when LS initialized and trigger scan if autoscan enabled`() {
    simulateRunningLS()
    justRun { lsMock.workspaceService.didChangeConfiguration(any<DidChangeConfigurationParams>()) }
    justRun { dumbServiceMock.runWhenSmart(any()) }
    every { dumbServiceMock.isDumb } returns false
    settings.scanOnSave = true

    cut.updateConfiguration(true)

    verify { lsMock.workspaceService.didChangeConfiguration(any<DidChangeConfigurationParams>()) }

    // scan only runs when smart
    verify { dumbServiceMock.runWhenSmart(any()) }
  }

  @Test
  fun `updateConfiguration should run when LS initialized and not trigger scan if autoscan disabled`() {
    simulateRunningLS()
    justRun { lsMock.workspaceService.didChangeConfiguration(any<DidChangeConfigurationParams>()) }
    justRun { dumbServiceMock.runWhenSmart(any()) }
    every { dumbServiceMock.isDumb } returns false
    settings.scanOnSave = false

    cut.updateConfiguration()

    verify { lsMock.workspaceService.didChangeConfiguration(any<DidChangeConfigurationParams>()) }

    // scan only runs when smart
    verify(exactly = 0) { dumbServiceMock.runWhenSmart(any()) }
  }

  @Test
  fun `sendFolderScanCommand does not run when dumb`() {
    simulateRunningLS()
    every { dumbServiceMock.isDumb } returns true

    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(null)

    cut.sendFolderScanCommand("testFolder", projectMock)

    verify(exactly = 0) { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
  }

  @Test
  fun `applyAiFixEdit should call language server`() {
    simulateRunningLS()
    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(null)

    val fixId = UUID.randomUUID().toString()
    cut.sendCodeApplyAiFixEditCommand(fixId)

    verify(exactly = 1) {
      lsMock.workspaceService.executeCommand(
        ExecuteCommandParams(COMMAND_CODE_FIX_APPLY_AI_EDIT, listOf(fixId))
      )
    }
  }

  @Test
  fun `ensureLanguageServerInitialized should not proceed when disposed`() {
    every { applicationMock.isDisposed } returns true

    val wrapper = LanguageServerWrapper(projectMock)
    assertFalse(wrapper.ensureLanguageServerInitialized())
  }

  @Test
  fun `shutdown should handle process termination`() {
    // Setup
    val processMock = mockk<Process>(relaxed = true)
    every { processMock.isAlive } returns true

    // Create wrapper instance
    val wrapper = LanguageServerWrapper(projectMock)
    wrapper.process = processMock
    wrapper.languageServer = lsMock
    wrapper.isInitialized = true

    // Add some test workspace folders
    val workspaceFolder = WorkspaceFolder("test://uri", "Test Folder")
    wrapper.configuredWorkspaceFolders.add(workspaceFolder)

    // Mock language server shutdown
    val completableFuture = CompletableFuture.completedFuture(Any())
    every { lsMock.shutdown() } returns completableFuture
    justRun { lsMock.exit() }

    // Mock loggers to avoid NPE
    mockkStatic("java.util.logging.Logger")
    val loggerMock = mockk<java.util.logging.Logger>(relaxed = true)
    every { java.util.logging.Logger.getLogger(any()) } returns loggerMock

    // Mock executor service
    val executorMock = mockk<java.util.concurrent.ExecutorService>(relaxed = true)
    val executorField = LanguageServerWrapper::class.java.getDeclaredField("executorService")
    executorField.isAccessible = true
    executorField.set(wrapper, executorMock)

    // Act
    wrapper.shutdown()

    // Assert
    // Check the workspace folders were cleared
    assertTrue(wrapper.configuredWorkspaceFolders.isEmpty())
    // Check the process was destroyed if alive
    verify { processMock.destroyForcibly() }
  }

  @Test
  fun `getConfigHtml should return HTML when LS responds with string`() {
    simulateRunningLS()
    val expectedHtml = "<html><body>Config</body></html>"
    every {
      lsMock.workspaceService.executeCommand(
        ExecuteCommandParams(COMMAND_WORKSPACE_CONFIGURATION, emptyList())
      )
    } returns CompletableFuture.completedFuture(expectedHtml)

    val result = cut.getConfigHtml()

    assertEquals(expectedHtml, result)
  }

  @Test
  fun `getConfigHtml should return null when LS not initialized`() {
    cut.isInitialized = false

    val result = cut.getConfigHtml()

    assertEquals(null, result)
  }

  @Test
  fun `getConfigHtml should return null when LS returns non-string`() {
    simulateRunningLS()
    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(mapOf("error" to "unexpected"))

    val result = cut.getConfigHtml()

    assertEquals(null, result)
  }

  @Test
  fun `getConfigHtml should return null when LS returns null`() {
    simulateRunningLS()
    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(null)

    val result = cut.getConfigHtml()

    assertEquals(null, result)
  }

  @Test
  fun getInitializationOptions() {
    settings.token = "testToken"
    settings.customEndpointUrl = "testEndpoint/"
    settings.ignoreUnknownCA = true
    settings.cliPath = "testCliPath"
    settings.organization = "org"
    val expectedTrustedFolders = listOf("/path/to/trusted1", "/path/to/trusted2")
    every { trustServiceMock.settings.getTrustedPaths() } returns expectedTrustedFolders

    val actual = cut.getInitializationOptions()

    assertEquals(
      settings.snykCodeSecurityIssuesScanEnable,
      actual.settings?.get("snyk_code_enabled")?.value,
    )
    assertEquals(settings.iacScanEnabled, actual.settings?.get("snyk_iac_enabled")?.value)
    assertEquals(settings.ossScanEnable, actual.settings?.get("snyk_oss_enabled")?.value)
    assertEquals(settings.token, actual.settings?.get("token")?.value)
    assertEquals(true, actual.settings?.get("token")?.changed)
    assertEquals(settings.ignoreUnknownCA, actual.settings?.get("proxy_insecure")?.value)
    assertEquals(getCliFile().absolutePath, actual.settings?.get("cli_path")?.value)
    assertEquals(settings.organization, actual.settings?.get("organization")?.value)
    assertEquals(settings.isDeltaFindingsEnabled(), actual.settings?.get("scan_net_new")?.value)
    assertEquals(expectedTrustedFolders, actual.trustedFolders)
  }

  @Test
  fun `getSettings should always mark token as changed`() {
    settings.token = "persistedToken"
    // token is NOT explicitly changed - simulates loading from persisted settings
    assertFalse(settings.isExplicitlyChanged("token"))

    val actual = cut.getSettings()

    assertEquals("persistedToken", actual.settings?.get("token")?.value)
    assertEquals(true, actual.settings?.get("token")?.changed)
  }

  @Test
  fun `getSettings should include manageBinariesAutomatically when true`() {
    settings.manageBinariesAutomatically = true

    val actual = cut.getSettings()

    assertEquals(true, actual.settings?.get("automatic_download")?.value)
  }

  @Test
  fun `getSettings should include manageBinariesAutomatically when false`() {
    settings.manageBinariesAutomatically = false

    val actual = cut.getSettings()

    assertEquals(false, actual.settings?.get("automatic_download")?.value)
  }

  @Test
  fun `getSettings sets changed true for product when value deviates from plugin default`() {
    settings.iacScanEnabled = false

    val actual = cut.getSettings()

    assertEquals(true, actual.settings?.get(LsFolderSettingsKeys.SNYK_IAC_ENABLED)?.changed)
  }

  @Test
  fun `getSettings sets changed true for automatic_download when value deviates from plugin default`() {
    settings.manageBinariesAutomatically = false

    val actual = cut.getSettings()

    assertEquals(true, actual.settings?.get(LsSettingsKeys.AUTOMATIC_DOWNLOAD)?.changed)
  }

  @Test
  fun `getWorkspaceFoldersFromRoots should return URIs without trailing slashes`() {
    // Create a real temporary directory to test with
    val tempDir = java.nio.file.Files.createTempDirectory("snyk-test-workspace")
    try {
      val pathString = tempDir.toAbsolutePath().toString()
      val virtualFile = mockk<VirtualFile>()
      every { virtualFile.path } returns pathString
      every { virtualFile.name } returns "snyk-test-workspace"
      every { virtualFile.isValid } returns true
      every { virtualFile.toNioPath() } returns tempDir

      // Mock UtilsKt extension and project methods
      every { projectMock.getContentRootVirtualFiles() } returns setOf(virtualFile)
      every { projectMock.basePath } returns pathString
      every { trustServiceMock.isPathTrusted(tempDir) } returns true

      val workspaceFolders = cut.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)

      assertEquals(1, workspaceFolders.size)
      val uri = workspaceFolders.first().uri
      assertFalse("URI should not end with slash: $uri", uri.endsWith("/"))
      assertTrue("URI should start with file:", uri.startsWith("file:"))
    } finally {
      java.nio.file.Files.deleteIfExists(tempDir)
    }
  }

  @Test
  fun `executeCommandWithArgs should return null when not initialized`() {
    cut.isInitialized = false

    val result = cut.executeCommandWithArgs("snyk.testCommand", listOf("arg1"))

    assertEquals(null, result)
  }

  @Test
  fun `executeCommandWithArgs should execute command and return result`() {
    simulateRunningLS()
    val expectedResult = "<html>tree</html>"
    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(expectedResult)

    val result = cut.executeCommandWithArgs("snyk.getTreeView", emptyList())

    assertEquals(expectedResult, result)
    verify {
      lsMock.workspaceService.executeCommand(
        match<ExecuteCommandParams> {
          it.command == "snyk.getTreeView" && it.arguments == emptyList<Any>()
        }
      )
    }
  }

  @Test
  fun `executeCommandWithArgs should pass arguments correctly`() {
    simulateRunningLS()
    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture("ok")

    val args = listOf("severity", "high", true)
    cut.executeCommandWithArgs("snyk.toggleTreeFilter", args)

    verify {
      lsMock.workspaceService.executeCommand(
        match<ExecuteCommandParams> {
          it.command == "snyk.toggleTreeFilter" && it.arguments == args
        }
      )
    }
  }

  @Test
  fun `executeCommandWithArgs should return null result from LS`() {
    simulateRunningLS()
    every { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) } returns
      CompletableFuture.completedFuture(null)

    val result = cut.executeCommandWithArgs("snyk.navigateToRange", listOf("/file.kt"))

    assertEquals(null, result)
  }

  @Test
  fun `getSettings includes per-folder product toggles from FolderConfigSettings`() {
    val folderPath = "/test/folder-toggles"
    val normalizedPath = java.nio.file.Paths.get(folderPath).normalize().toAbsolutePath().toString()
    val folderUri = java.nio.file.Paths.get(folderPath).toUri().toASCIIString().removeSuffix("/")

    val folderConfig =
      LspFolderConfig(
        folderPath = normalizedPath,
        settings =
          mapOf(
            LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = true),
            LsFolderSettingsKeys.SNYK_OSS_ENABLED to ConfigSetting(value = false),
            LsFolderSettingsKeys.SNYK_IAC_ENABLED to ConfigSetting(value = true),
            LsFolderSettingsKeys.SNYK_SECRETS_ENABLED to ConfigSetting(value = false),
          ),
      )

    every { folderConfigSettingsMock.getFolderConfig(normalizedPath) } returns folderConfig

    cut.configuredWorkspaceFolders.add(WorkspaceFolder(folderUri, "folder-toggles"))
    cut.updateFolderConfigRefresh(normalizedPath, true)

    val result = cut.getSettings()

    val outputFolderConfigs = result.folderConfigs
    assertEquals("Should have one folder config", 1, outputFolderConfigs?.size)

    val outputSettings = outputFolderConfigs?.first()?.settings
    assertEquals(
      "code toggle should be true",
      true,
      outputSettings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)?.value,
    )
    assertEquals(
      "oss toggle should be false",
      false,
      outputSettings?.get(LsFolderSettingsKeys.SNYK_OSS_ENABLED)?.value,
    )
    assertEquals(
      "iac toggle should be true",
      true,
      outputSettings?.get(LsFolderSettingsKeys.SNYK_IAC_ENABLED)?.value,
    )
    assertEquals(
      "secrets toggle should be false",
      false,
      outputSettings?.get(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED)?.value,
    )
  }

  @Test
  fun `getSettings includes API_ENDPOINT from settings state`() {
    settings.customEndpointUrl = "https://api.custom.io"

    val actual = cut.getSettings()

    assertEquals("https://api.custom.io", actual.settings?.get(LsSettingsKeys.API_ENDPOINT)?.value)
  }

  @Test
  fun `getSettings includes CLI_PATH from settings state`() {
    settings.cliPath = "/usr/local/bin/snyk"

    val actual = cut.getSettings()

    assertEquals(
      java.io.File("/usr/local/bin/snyk").absolutePath,
      actual.settings?.get(LsSettingsKeys.CLI_PATH)?.value,
    )
  }

  @Test
  fun `getSettings includes all round-trip machine-scope keys`() {
    settings.customEndpointUrl = "https://api.custom.io"
    settings.ignoreUnknownCA = true
    settings.organization = "test-org"
    settings.manageBinariesAutomatically = false
    settings.cliPath = "/usr/local/bin/snyk"
    settings.cliBaseDownloadURL = "https://static.snyk.io"
    settings.cliReleaseChannel = "preview"

    val actual = cut.getSettings()
    val s = actual.settings!!

    assertEquals("https://api.custom.io", s[LsSettingsKeys.API_ENDPOINT]?.value)
    assertEquals(true, s[LsSettingsKeys.PROXY_INSECURE]?.value)
    assertEquals("test-org", s[LsSettingsKeys.ORGANIZATION]?.value)
    assertEquals(false, s[LsSettingsKeys.AUTOMATIC_DOWNLOAD]?.value)
    assertEquals(
      java.io.File("/usr/local/bin/snyk").absolutePath,
      s[LsSettingsKeys.CLI_PATH]?.value,
    )
    assertEquals("https://static.snyk.io", s[LsSettingsKeys.BINARY_BASE_URL]?.value)
    assertEquals("preview", s[LsSettingsKeys.CLI_RELEASE_CHANNEL]?.value)
  }

  @Test
  fun `getSettings includes write-only keys`() {
    settings.token = "test-token-value"

    val actual = cut.getSettings()
    val s = actual.settings!!

    assertEquals("test-token-value", s[LsSettingsKeys.TOKEN]?.value)
    assertEquals(true, s[LsSettingsKeys.SEND_ERROR_REPORTS]?.value)
    assertEquals(true, s[LsSettingsKeys.ENABLE_SNYK_OSS_QUICK_FIX_CODE_ACTIONS]?.value)
    assertEquals(true, s[LsSettingsKeys.ENABLE_SNYK_OPEN_BROWSER_ACTIONS]?.value)
  }

  @Test
  fun `updateWorkspaceFolders adds and removes folders`() {
    simulateRunningLS()
    justRun { lsMock.workspaceService.didChangeWorkspaceFolders(any()) }

    val folder1 = WorkspaceFolder("file:///test/folder1", "folder1")
    val folder2 = WorkspaceFolder("file:///test/folder2", "folder2")

    cut.updateWorkspaceFolders(setOf(folder1, folder2), emptySet())

    assertTrue(cut.configuredWorkspaceFolders.contains(folder1))
    assertTrue(cut.configuredWorkspaceFolders.contains(folder2))

    cut.updateWorkspaceFolders(emptySet(), setOf(folder1))

    assertFalse(cut.configuredWorkspaceFolders.contains(folder1))
    assertTrue(cut.configuredWorkspaceFolders.contains(folder2))
  }

  @Test
  fun `updateWorkspaceFolders does not send when no changes`() {
    simulateRunningLS()
    justRun { lsMock.workspaceService.didChangeWorkspaceFolders(any()) }

    val folder1 = WorkspaceFolder("file:///test/folder1", "folder1")
    cut.configuredWorkspaceFolders.add(folder1)

    // Adding already-configured folder should not trigger
    cut.updateWorkspaceFolders(setOf(folder1), emptySet())

    verify(exactly = 0) { lsMock.workspaceService.didChangeWorkspaceFolders(any()) }
  }

  @Test
  fun `updateWorkspaceFolders does not run when disposed`() {
    every { applicationMock.isDisposed } returns true

    cut.updateWorkspaceFolders(setOf(WorkspaceFolder("file:///test", "test")), emptySet())

    verify(exactly = 0) { lsMock.workspaceService.didChangeWorkspaceFolders(any()) }
  }

  @Test
  fun `getFolderConfigsRefreshed returns unmodifiable map`() {
    val result = cut.getFolderConfigsRefreshed()
    assertTrue(result.isEmpty())
  }

  @Test
  fun `updateFolderConfigRefresh stores normalized path`() {
    cut.updateFolderConfigRefresh("/test/folder", true)

    val refreshed = cut.getFolderConfigsRefreshed()
    assertTrue(refreshed.values.contains(true))
    assertEquals(1, refreshed.size)

    cut.updateFolderConfigRefresh("/test/folder", false)

    val refreshed2 = cut.getFolderConfigsRefreshed()
    assertTrue(refreshed2.values.contains(false))
  }

  @Test
  fun `isDisposed returns true when application is disposed`() {
    every { applicationMock.isDisposed } returns true
    assertTrue(cut.isDisposed())
  }

  @Test
  fun `isDisposed returns false when application is not disposed`() {
    every { applicationMock.isDisposed } returns false
    assertFalse(cut.isDisposed())
  }

  @Test
  fun `dispose sets disposed flag`() {
    // Create a wrapper but don't start the process, so shutdown works trivially
    val wrapper = LanguageServerWrapper(projectMock)
    wrapper.languageServer = lsMock
    wrapper.isInitialized = false
    every { applicationMock.isDisposed } returns false

    assertFalse(wrapper.isDisposed())
    wrapper.dispose()
    assertTrue(wrapper.isDisposed())
  }

  @Test
  fun `getSettings includes severity filter with all severity states`() {
    settings.criticalSeverityEnabled = true
    settings.highSeverityEnabled = false
    settings.mediumSeverityEnabled = true
    settings.lowSeverityEnabled = false

    val actual = cut.getSettings()

    assertEquals(true, actual.settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)?.value)
    assertEquals(false, actual.settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH)?.value)
    assertEquals(true, actual.settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM)?.value)
    assertEquals(false, actual.settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_LOW)?.value)
  }

  @Test
  fun `getSettings includes issue view options`() {
    settings.openIssuesEnabled = false
    settings.ignoredIssuesEnabled = true
    settings.scanOnSave = false

    val actual = cut.getSettings()
    val s = actual.settings!!

    assertEquals(false, s[LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES]?.value)
    assertEquals(true, s[LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES]?.value)
    assertEquals(false, s[LsFolderSettingsKeys.SCAN_AUTOMATIC]?.value)
  }

  @Test
  fun `getSettings includes scan net new setting`() {
    settings.setDeltaEnabled(true)

    val actual = cut.getSettings()
    assertEquals(true, actual.settings?.get(LsFolderSettingsKeys.SCAN_NET_NEW)?.value)
  }

  @Test
  fun `getSettings includes risk score threshold when set`() {
    settings.riskScoreThreshold = 500

    val actual = cut.getSettings()
    assertEquals(500, actual.settings?.get(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)?.value)
  }

  @Test
  fun `getSettings omits risk score threshold when null`() {
    settings.riskScoreThreshold = null

    val actual = cut.getSettings()
    assertFalse(actual.settings?.containsKey(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD) ?: true)
  }

  @Test
  fun `getSettings includes authentication method`() {
    settings.authenticationType = io.snyk.plugin.services.AuthenticationType.PAT

    val actual = cut.getSettings()
    assertEquals("pat", actual.settings?.get(LsSettingsKeys.AUTHENTICATION_METHOD)?.value)
  }

  @Test
  fun `getSettings omits token when blank`() {
    settings.token = ""

    val actual = cut.getSettings()
    assertFalse(actual.settings?.containsKey(LsSettingsKeys.TOKEN) ?: true)
  }

  @Test
  fun `getSettings emits reset signal for pending resets`() {
    settings.addPendingReset(LsSettingsKeys.ORGANIZATION)

    val actual = cut.getSettings()

    val orgSetting = actual.settings?.get(LsSettingsKeys.ORGANIZATION)
    assertNotNull("Organization should be present in settings", orgSetting)
    assertEquals("Reset signal value should be null", null, orgSetting!!.value)
    assertEquals("Reset signal changed should be true", true, orgSetting.changed)
  }

  @Test
  fun `getSettings consumes pending resets (one-shot)`() {
    settings.addPendingReset(LsSettingsKeys.ORGANIZATION)

    // First call should include the reset signal
    val first = cut.getSettings()
    val orgFirst = first.settings?.get(LsSettingsKeys.ORGANIZATION)
    assertNotNull(orgFirst)
    assertEquals(null, orgFirst!!.value)
    assertEquals(true, orgFirst.changed)

    // Second call should NOT include the reset signal (consumed)
    settings.organization = "real-org"
    val second = cut.getSettings()
    val orgSecond = second.settings?.get(LsSettingsKeys.ORGANIZATION)
    assertNotNull(orgSecond)
    // Should now have the real value, not null
    assertEquals("real-org", orgSecond!!.value)
  }

  @Test
  fun `getSettings reset signal overrides normal value for pending key`() {
    // Set a normal value for organization
    settings.organization = "my-org"
    // Then add a pending reset -- the reset should win
    settings.addPendingReset(LsSettingsKeys.ORGANIZATION)

    val actual = cut.getSettings()

    val orgSetting = actual.settings?.get(LsSettingsKeys.ORGANIZATION)
    assertNotNull(orgSetting)
    assertEquals("Reset should override the normal value with null", null, orgSetting!!.value)
    assertEquals(true, orgSetting.changed)
  }

  @Test
  fun `getSettings includes explicitly changed flags`() {
    settings.markExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE)

    val actual = cut.getSettings()
    assertEquals(true, actual.settings?.get(LsSettingsKeys.PROXY_INSECURE)?.changed)
  }

  @Test
  fun `getSettings folder configs only include refreshed folders`() {
    val folderPath = "/test/refreshed-folder"
    val normalizedPath = Paths.get(folderPath).normalize().toAbsolutePath().toString()
    val folderUri = Paths.get(folderPath).toUri().toASCIIString().removeSuffix("/")

    val unrefreshedPath = "/test/not-refreshed"
    val unrefreshedNormalized = Paths.get(unrefreshedPath).normalize().toAbsolutePath().toString()
    val unrefreshedUri = Paths.get(unrefreshedPath).toUri().toASCIIString().removeSuffix("/")

    val folderConfig =
      LspFolderConfig(
        folderPath = normalizedPath,
        settings = mapOf(LsFolderSettingsKeys.BASE_BRANCH to ConfigSetting(value = "main")),
      )
    val unrefreshedConfig =
      LspFolderConfig(
        folderPath = unrefreshedNormalized,
        settings = mapOf(LsFolderSettingsKeys.BASE_BRANCH to ConfigSetting(value = "develop")),
      )

    every { folderConfigSettingsMock.getFolderConfig(normalizedPath) } returns folderConfig
    every { folderConfigSettingsMock.getFolderConfig(unrefreshedNormalized) } returns
      unrefreshedConfig

    cut.configuredWorkspaceFolders.add(WorkspaceFolder(folderUri, "refreshed"))
    cut.configuredWorkspaceFolders.add(WorkspaceFolder(unrefreshedUri, "not-refreshed"))

    // Only mark one as refreshed
    cut.updateFolderConfigRefresh(normalizedPath, true)

    val result = cut.getSettings()

    assertEquals("Should only include the refreshed folder", 1, result.folderConfigs?.size)
  }

  @Test
  fun `getSettings applies persisted folder changed flags`() {
    val folderPath = "/test/changed-folder"
    val normalizedPath = Paths.get(folderPath).normalize().toAbsolutePath().toString()
    val folderUri = Paths.get(folderPath).toUri().toASCIIString().removeSuffix("/")

    val folderConfig =
      LspFolderConfig(
        folderPath = normalizedPath,
        settings =
          mapOf(LsFolderSettingsKeys.BASE_BRANCH to ConfigSetting(value = "main", changed = false)),
      )

    every { folderConfigSettingsMock.getFolderConfig(normalizedPath) } returns folderConfig

    cut.configuredWorkspaceFolders.add(WorkspaceFolder(folderUri, "changed"))
    cut.updateFolderConfigRefresh(normalizedPath, true)

    // Mark base_branch as explicitly changed for this folder
    settings.markExplicitlyChanged(normalizedPath, LsFolderSettingsKeys.BASE_BRANCH)

    val result = cut.getSettings()

    val outputConfig = result.folderConfigs?.first()
    assertEquals(
      "Changed flag should be overridden to true",
      true,
      outputConfig?.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.changed,
    )
  }

  @Test
  fun `verifyCliProtocolVersion returns true when CLI prints required version`() {
    assumeFalse("Mock CLI scripts are POSIX shell scripts", SystemUtils.IS_OS_WINDOWS)
    val scenarios =
      listOf(
        VerifyProtocolScenario(
          name = "exact match",
          script = "echo ${settings.requiredLsProtocolVersion}",
          expected = true,
        ),
        VerifyProtocolScenario(
          name = "exact match with trailing newline whitespace",
          script = "printf '${settings.requiredLsProtocolVersion}\\n'",
          expected = true,
        ),
        VerifyProtocolScenario(
          name = "exact match with surrounding whitespace",
          script = "printf '  ${settings.requiredLsProtocolVersion}  \\n'",
          expected = true,
        ),
        VerifyProtocolScenario(name = "version mismatch", script = "echo 1", expected = false),
        VerifyProtocolScenario(
          name = "non-integer output",
          script = "echo not-a-number",
          expected = false,
        ),
        VerifyProtocolScenario(name = "empty output", script = "true", expected = false),
        VerifyProtocolScenario(
          name = "non-zero exit code",
          script = "echo ${settings.requiredLsProtocolVersion}\nexit 2",
          expected = false,
        ),
      )
    for (scenario in scenarios) {
      val scriptFile =
        java.io.File.createTempFile("snyk-cli-mock-", ".sh").apply {
          writeText("#!/bin/sh\n${scenario.script}\n")
          setExecutable(true)
          deleteOnExit()
        }
      try {
        every { getCliFile() } returns scriptFile

        val result = cut.verifyCliProtocolVersion()

        assertEquals("scenario '${scenario.name}'", scenario.expected, result)
      } finally {
        scriptFile.delete()
      }
    }
  }

  @Test
  fun `verifyCliProtocolVersion returns false when CLI binary cannot be executed`() {
    val nonExistent = java.io.File("/nonexistent/snyk-cli-${UUID.randomUUID()}")
    every { getCliFile() } returns nonExistent

    val result = cut.verifyCliProtocolVersion()

    assertFalse(result)
  }

  @Test
  fun `verifyCliProtocolVersion updates currentLSProtocolVersion when CLI returns integer`() {
    assumeFalse("Mock CLI scripts are POSIX shell scripts", SystemUtils.IS_OS_WINDOWS)
    val scriptFile =
      java.io.File.createTempFile("snyk-cli-mock-", ".sh").apply {
        writeText("#!/bin/sh\necho 7\n")
        setExecutable(true)
        deleteOnExit()
      }
    try {
      every { getCliFile() } returns scriptFile

      cut.verifyCliProtocolVersion()

      assertEquals(7, settings.currentLSProtocolVersion)
    } finally {
      scriptFile.delete()
    }
  }

  private data class VerifyProtocolScenario(
    val name: String,
    val script: String,
    val expected: Boolean,
  )

  private fun simulateRunningLS() {
    cut.languageClient = mockk(relaxed = true)
    val processMock = mockk<Process>(relaxed = true)
    cut.process = processMock
    every { processMock.info().startInstant().isPresent } returns true
    every { processMock.isAlive } returns true
  }
}
