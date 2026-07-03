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
import junit.framework.TestCase.assertNull
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
    // getSettings normalizes each workspace-folder path via this; the real impl is identity for the
    // already-normalized absolute paths these tests use, so pass the arg through (relaxed mock
    // would otherwise return null and drop every folder config).
    every { folderConfigSettingsMock.normalizePathOrNull(any()) } answers { firstArg() }
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
  fun `getSettings emits one-shot null reset signal then does not re-push on reconnect`() {
    // Simulate a global reset applied to persisted state: the override was cleared, the value
    // restored to its plugin default, and a pending reset queued.
    settings.iacScanEnabled = true // restored default; deviation check must be false
    settings.clearExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    settings.addPendingReset(LsFolderSettingsKeys.SNYK_IAC_ENABLED)

    // First getSettings (e.g. didChangeConfiguration after save) emits the one-shot reset.
    val first = cut.getSettings()
    val firstSetting = first.settings?.get(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    assertNull("reset must carry value=null", firstSetting?.value)
    assertEquals("reset must carry changed=true", true, firstSetting?.changed)

    // Second getSettings (simulating reconnect/initialize after the pending reset was consumed)
    // must NOT re-assert the override: value is the default and changed must be false.
    val second = cut.getSettings()
    val secondSetting = second.settings?.get(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    assertEquals("default value re-published", true, secondSetting?.value)
    assertEquals(
      "no changed:true on reconnect -- override must stay cleared",
      false,
      secondSetting?.changed,
    )
  }

  @Test
  fun `getSettings emits changed true when field re-asserted to its default value after reset (ADR-1)`() {
    // Regression for IDE-2149 / ADR-1: after a reset restored the field to its plugin default and
    // the pending reset was consumed, the user re-enables the field to that SAME default value.
    // SaveConfigHandler marks it explicitly changed, so getSettings must emit changed:true (with
    // the
    // real value, NOT value=null) even though the value equals the default. Previously the
    // value-equals-default auto-clear made getSettings emit changed:false and the LS never
    // relearned
    // the override.
    settings.iacScanEnabled =
      true // equals the plugin default; deviation check alone would be false
    settings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)

    val actual = cut.getSettings()
    val setting = actual.settings?.get(LsFolderSettingsKeys.SNYK_IAC_ENABLED)

    assertEquals("re-asserted value must be published", true, setting?.value)
    assertEquals("re-asserted override must be changed:true", true, setting?.changed)
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

      val workspaceFolders = cut.getWorkspaceFoldersFromRoots(projectMock)

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
  fun `getSettings emits every configured folder including a reset never echoed by the LS`() {
    // No IDE-side refresh gate (mirrors VS Code): every configured folder is emitted. A folder
    // reset
    // {value:null, changed:true} must reach the LS even though the LS never echoed this folder's
    // config — the bug the old refresh-flag gate caused by dropping such folders.
    val plainPath = "/test/plain-folder"
    val plainNormalized = Paths.get(plainPath).normalize().toAbsolutePath().toString()
    val plainUri = Paths.get(plainPath).toUri().toASCIIString().removeSuffix("/")

    val resetPath = "/test/reset-folder"
    val resetNormalized = Paths.get(resetPath).normalize().toAbsolutePath().toString()
    val resetUri = Paths.get(resetPath).toUri().toASCIIString().removeSuffix("/")

    val plainConfig =
      LspFolderConfig(
        folderPath = plainNormalized,
        settings = mapOf(LsFolderSettingsKeys.BASE_BRANCH to ConfigSetting(value = "main")),
      )
    val resetConfig =
      LspFolderConfig(
        folderPath = resetNormalized,
        settings =
          mapOf(
            LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = null, changed = true)
          ),
      )

    every { folderConfigSettingsMock.getFolderConfig(plainNormalized) } returns plainConfig
    every { folderConfigSettingsMock.getFolderConfig(resetNormalized) } returns resetConfig

    cut.configuredWorkspaceFolders.add(WorkspaceFolder(plainUri, "plain"))
    cut.configuredWorkspaceFolders.add(WorkspaceFolder(resetUri, "reset"))

    val result = cut.getSettings()

    assertEquals("Both configured folders are emitted", 2, result.folderConfigs?.size)
    val emittedReset = result.folderConfigs?.first { it.folderPath == resetNormalized }
    assertEquals(
      "Reset folder ships value=null changed=true with no refresh flag set",
      null,
      emittedReset?.settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)?.value,
    )
    assertEquals(true, emittedReset?.settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)?.changed)
  }

  @Test
  fun `getSettings emits the stored folder config changed flag verbatim`() {
    // The stored LspFolderConfig is the single source of truth for the changed flag; getSettings
    // emits it as-is with no re-derivation. A folder writer (e.g. the reset path) sets changed=true
    // on the stored config and that flag reaches the LS unchanged.
    val folderPath = "/test/changed-folder"
    val normalizedPath = Paths.get(folderPath).normalize().toAbsolutePath().toString()
    val folderUri = Paths.get(folderPath).toUri().toASCIIString().removeSuffix("/")

    val folderConfig =
      LspFolderConfig(
        folderPath = normalizedPath,
        settings =
          mapOf(LsFolderSettingsKeys.BASE_BRANCH to ConfigSetting(value = "main", changed = true)),
      )

    every { folderConfigSettingsMock.getFolderConfig(normalizedPath) } returns folderConfig

    cut.configuredWorkspaceFolders.add(WorkspaceFolder(folderUri, "changed"))

    val result = cut.getSettings()

    val outputConfig = result.folderConfigs?.first()
    assertEquals(
      "Stored changed flag is emitted verbatim",
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

  @Test
  fun `getSettings emits a stored folder reset as value null changed true`() {
    // A reset is written straight into the stored folder config as {value:null, changed:true}
    // (SaveConfigHandler.applyFolderResetsFromRawJson, mirroring VS Code). getSettings emits the
    // stored settings map verbatim, so the null reaches the LS as an Unset signal.
    val folderPath = Paths.get("/work/reset-project").toAbsolutePath().toString()
    val storedReset =
      LspFolderConfig(
        folderPath = folderPath,
        settings =
          mapOf(
            LsFolderSettingsKeys.SNYK_CODE_ENABLED to ConfigSetting(value = null, changed = true),
            LsFolderSettingsKeys.PREFERRED_ORG to ConfigSetting(value = null, changed = true),
            LsFolderSettingsKeys.SCAN_COMMAND_CONFIG to ConfigSetting(value = null, changed = true),
          ),
      )
    every { folderConfigSettingsMock.getFolderConfig(folderPath) } returns storedReset
    cut.configuredWorkspaceFolders.add(
      WorkspaceFolder(Paths.get(folderPath).toUri().toASCIIString(), folderPath)
    )

    val actual = cut.getSettings()

    val folderConfig =
      actual.folderConfigs?.firstOrNull { it.folderPath == folderPath }
        ?: error("expected a folder config carrying the reset for $folderPath")
    for (key in
      listOf(
        LsFolderSettingsKeys.SNYK_CODE_ENABLED,
        LsFolderSettingsKeys.PREFERRED_ORG,
        LsFolderSettingsKeys.SCAN_COMMAND_CONFIG,
      )) {
      val setting = folderConfig.settings?.get(key) ?: error("expected $key reset setting")
      assertNull(setting.value)
      assertEquals(true, setting.changed)
    }
  }

  @Test
  fun `getSettings emits the authoritative value after the LS pushes it back over a reset`() {
    // snyk-ls owns config: after the IDE sends the reset it Unsets the override, recomputes, and
    // pushes the resolved config back via addAll (full overwrite of the stored LspFolderConfig).
    // The transient null is replaced, so getSettings then emits the authoritative value.
    val folderPath = Paths.get("/work/push-back-project").toAbsolutePath().toString()
    cut.configuredWorkspaceFolders.add(
      WorkspaceFolder(Paths.get(folderPath).toUri().toASCIIString(), folderPath)
    )

    // Before push-back: stored config carries the reset null.
    every { folderConfigSettingsMock.getFolderConfig(folderPath) } returns
      LspFolderConfig(
        folderPath = folderPath,
        settings =
          mapOf(LsFolderSettingsKeys.PREFERRED_ORG to ConfigSetting(value = null, changed = true)),
      )
    val beforeSetting =
      cut
        .getSettings()
        .folderConfigs
        ?.firstOrNull { it.folderPath == folderPath }
        ?.settings
        ?.get(LsFolderSettingsKeys.PREFERRED_ORG)
        ?: error("expected reset setting before push-back")
    assertNull(beforeSetting.value)

    // After push-back: addAll overwrote the stored config with the resolved value.
    every { folderConfigSettingsMock.getFolderConfig(folderPath) } returns
      LspFolderConfig(
        folderPath = folderPath,
        settings =
          mapOf(
            LsFolderSettingsKeys.PREFERRED_ORG to
              ConfigSetting(value = "resolved-org", changed = true)
          ),
      )
    val afterSetting =
      cut
        .getSettings()
        .folderConfigs
        ?.firstOrNull { it.folderPath == folderPath }
        ?.settings
        ?.get(LsFolderSettingsKeys.PREFERRED_ORG) ?: error("expected setting after push-back")
    assertEquals("resolved-org", afterSetting.value)
  }

  @Test
  fun `getSettings emits global additional_parameters when set`() {
    settings.globalAdditionalParameters = "--severity-threshold=high --debug"

    val actual = cut.getSettings()

    assertEquals(
      "--severity-threshold=high --debug",
      actual.settings?.get(LsSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
    assertEquals(true, actual.settings?.get(LsSettingsKeys.ADDITIONAL_PARAMETERS)?.changed)
  }

  @Test
  fun `getSettings emits global additional_environment when set`() {
    settings.globalAdditionalEnvironment = "VAR1=value1;VAR2=value2"

    val actual = cut.getSettings()

    assertEquals(
      "VAR1=value1;VAR2=value2",
      actual.settings?.get(LsSettingsKeys.ADDITIONAL_ENVIRONMENT)?.value,
    )
    assertEquals(true, actual.settings?.get(LsSettingsKeys.ADDITIONAL_ENVIRONMENT)?.changed)
  }

  @Test
  fun `getSettings emits global additional_parameters with changed=false when blank`() {
    settings.globalAdditionalParameters = ""

    val actual = cut.getSettings()

    assertEquals("", actual.settings?.get(LsSettingsKeys.ADDITIONAL_PARAMETERS)?.value)
    assertEquals(false, actual.settings?.get(LsSettingsKeys.ADDITIONAL_PARAMETERS)?.changed)
  }

  @Test
  fun `getSettings emits global additional_environment with changed=false when blank`() {
    settings.globalAdditionalEnvironment = ""

    val actual = cut.getSettings()

    assertEquals("", actual.settings?.get(LsSettingsKeys.ADDITIONAL_ENVIRONMENT)?.value)
    assertEquals(false, actual.settings?.get(LsSettingsKeys.ADDITIONAL_ENVIRONMENT)?.changed)
  }

  // ── Scenario 2: ADR-1 re-assert for MULTIPLE key kinds ──────────────────────
  // After a reset restored a field to its plugin default and the pending reset was consumed, the
  // user re-asserts that SAME default value. SaveConfigHandler marks it explicitly changed, so
  // getSettings must emit changed:true (with the real value, not null) even though the value equals
  // the default. The single existing case covers snyk_code_enabled; this extends to a severity key,
  // a scan-mode key, and risk_score_threshold. Mirrors vscode "does not skip when value equals
  // schema default but differs from effective value".
  @Test
  fun `getSettings emits changed true when severity re-asserted to default after reset (ADR-1)`() {
    settings.criticalSeverityEnabled = true // equals plugin default; deviation alone would be false
    settings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)

    val setting = cut.getSettings().settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)

    assertEquals("re-asserted value must be published", true, setting?.value)
    assertEquals("re-asserted override must be changed:true", true, setting?.changed)
  }

  @Test
  fun `getSettings emits changed true when scan_automatic re-asserted to default after reset (ADR-1)`() {
    settings.scanOnSave = true // equals plugin default
    settings.markExplicitlyChanged(LsFolderSettingsKeys.SCAN_AUTOMATIC)

    val setting = cut.getSettings().settings?.get(LsFolderSettingsKeys.SCAN_AUTOMATIC)

    assertEquals("re-asserted value must be published", true, setting?.value)
    assertEquals("re-asserted override must be changed:true", true, setting?.changed)
  }

  @Test
  fun `getSettings emits changed true when scan_net_new re-asserted to default after reset (ADR-1)`() {
    settings.setDeltaEnabled(false) // equals plugin default (false)
    settings.markExplicitlyChanged(LsFolderSettingsKeys.SCAN_NET_NEW)

    val setting = cut.getSettings().settings?.get(LsFolderSettingsKeys.SCAN_NET_NEW)

    assertEquals("re-asserted value must be published", false, setting?.value)
    assertEquals("re-asserted override must be changed:true", true, setting?.changed)
  }

  @Test
  fun `getSettings emits changed true when risk_score_threshold re-asserted after reset (ADR-1)`() {
    // risk_score_threshold has no non-null default; any concrete re-assert must be changed:true.
    settings.riskScoreThreshold = 700
    settings.markExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)

    val setting = cut.getSettings().settings?.get(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)

    assertEquals("re-asserted value must be published", 700, setting?.value)
    assertEquals("re-asserted override must be changed:true", true, setting?.changed)
  }

  // ── Scenario 3: DEDUP / all-severity-together (emission side) ────────────────
  @Test
  fun `getSettings emits null reset for all four severity filters at once then default on reconnect`() {
    // All four severity_filter_* reset together: restored to default (true), override cleared, all
    // four pending resets queued (mirrors save-side dedup). getSettings emits {null,true} for each
    // once, then {default,false} on the second call. Mirrors vscode "persists non-reset entries
    // while resetting reset entries" applied to the four siblings sharing a reset.
    val severityKeys =
      listOf(
        LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL,
        LsFolderSettingsKeys.SEVERITY_FILTER_HIGH,
        LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM,
        LsFolderSettingsKeys.SEVERITY_FILTER_LOW,
      )
    // Post-reset state: defaults restored, overrides cleared, resets queued.
    settings.criticalSeverityEnabled = true
    settings.highSeverityEnabled = true
    settings.mediumSeverityEnabled = true
    settings.lowSeverityEnabled = true
    for (key in severityKeys) {
      settings.clearExplicitlyChanged(key)
      settings.addPendingReset(key)
    }

    val first = cut.getSettings().settings!!
    for (key in severityKeys) {
      assertNull("$key: first emit must carry value=null", first[key]?.value)
      assertEquals("$key: first emit must carry changed=true", true, first[key]?.changed)
    }

    val second = cut.getSettings().settings!!
    for (key in severityKeys) {
      assertEquals("$key: second emit re-publishes default value", true, second[key]?.value)
      assertEquals("$key: second emit must be changed=false", false, second[key]?.changed)
    }
  }

  // ── Scenario 4: MIXED batch (emission side) ─────────────────────────────────
  @Test
  fun `getSettings emits null for reset keys and concrete changed values for non-reset keys`() {
    // Mixed post-save state: organization + risk_score_threshold were reset (default restored,
    // override cleared, reset queued); snyk_iac_enabled + severity_filter_critical were concretely
    // asserted (marked explicitly changed). One getSettings must emit {null,true} for the reset
    // keys
    // AND the concrete value with changed:true for the others; a second call clears the resets to
    // changed:false. Mirrors vscode "persists non-reset entries while resetting reset entries".
    settings.organization = null
    settings.clearExplicitlyChanged(LsSettingsKeys.ORGANIZATION)
    settings.addPendingReset(LsSettingsKeys.ORGANIZATION)
    settings.riskScoreThreshold = null
    settings.clearExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)
    settings.addPendingReset(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)

    settings.iacScanEnabled = false
    settings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    settings.criticalSeverityEnabled = true
    settings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)

    val first = cut.getSettings().settings!!

    // Reset keys emit {null, true}.
    assertNull(first[LsSettingsKeys.ORGANIZATION]?.value)
    assertEquals(true, first[LsSettingsKeys.ORGANIZATION]?.changed)
    // risk_score_threshold only appears in the map because a reset was queued (value would be
    // omitted when null otherwise).
    assertNull(first[LsFolderSettingsKeys.RISK_SCORE_THRESHOLD]?.value)
    assertEquals(true, first[LsFolderSettingsKeys.RISK_SCORE_THRESHOLD]?.changed)

    // Concrete keys emit their value with changed:true.
    assertEquals(false, first[LsFolderSettingsKeys.SNYK_IAC_ENABLED]?.value)
    assertEquals(true, first[LsFolderSettingsKeys.SNYK_IAC_ENABLED]?.changed)
    assertEquals(true, first[LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL]?.value)
    assertEquals(true, first[LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL]?.changed)

    // Second getSettings: reset for organization is consumed, so it now re-publishes the default
    // (null organization is omitted from the map entirely) and no longer carries a null reset.
    val second = cut.getSettings().settings!!
    assertFalse(
      "organization reset is one-shot; null org must be omitted on the second call",
      second.containsKey(LsSettingsKeys.ORGANIZATION),
    )
    // Concrete keys remain changed:true across calls.
    assertEquals(true, second[LsFolderSettingsKeys.SNYK_IAC_ENABLED]?.changed)
    assertEquals(true, second[LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL]?.changed)
  }

  // ── Scenario 6: one-shot exactly-once for additional key types ───────────────
  // The existing exactly-once cases cover ORGANIZATION and SNYK_IAC_ENABLED. Extend to a severity
  // filter and a scan-mode key to guard the re-push/reconnect path for those types too.
  @Test
  fun `getSettings emits severity reset exactly once then default with changed false on reconnect`() {
    settings.criticalSeverityEnabled = true // restored default; deviation check must be false
    settings.clearExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)
    settings.addPendingReset(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)

    val first = cut.getSettings().settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)
    assertNull("first emit must carry value=null", first?.value)
    assertEquals("first emit must carry changed=true", true, first?.changed)

    val second = cut.getSettings().settings?.get(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)
    assertEquals("reconnect re-publishes default value", true, second?.value)
    assertEquals("reconnect must not re-assert override", false, second?.changed)
  }

  @Test
  fun `getSettings emits scan_net_new reset exactly once then default with changed false on reconnect`() {
    settings.setDeltaEnabled(false) // restored default (false); deviation check must be false
    settings.clearExplicitlyChanged(LsFolderSettingsKeys.SCAN_NET_NEW)
    settings.addPendingReset(LsFolderSettingsKeys.SCAN_NET_NEW)

    val first = cut.getSettings().settings?.get(LsFolderSettingsKeys.SCAN_NET_NEW)
    assertNull("first emit must carry value=null", first?.value)
    assertEquals("first emit must carry changed=true", true, first?.changed)

    val second = cut.getSettings().settings?.get(LsFolderSettingsKeys.SCAN_NET_NEW)
    assertEquals("reconnect re-publishes default value", false, second?.value)
    assertEquals("reconnect must not re-assert override", false, second?.changed)
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
