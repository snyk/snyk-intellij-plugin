package snyk.common.lsp

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.analytics.ScanDoneEvent
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class LanguageServerWrapperTest {
    private val folderConfigSettingsMock: FolderConfigSettings = mockk(relaxed = true)
    private val applicationMock: Application = mockk(relaxed = true)
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
        every { applicationMock.getService(SnykPluginDisposable::class.java) } returns snykPluginDisposable
        every { applicationMock.getService(FolderConfigSettings::class.java) } returns folderConfigSettingsMock
        every { applicationMock.isDisposed } returns false

        every { projectManagerMock.openProjects } returns arrayOf(projectMock)
        every { projectMock.isDisposed } returns false
        every { projectMock.getService(DumbService::class.java) } returns dumbServiceMock
        every { dumbServiceMock.isDumb } returns false

        every { pluginSettings() } returns settings
        mockkStatic("snyk.PluginInformationKt")
        every { pluginInfo } returns mockk(relaxed = true)
        every { pluginInfo.integrationName } returns "Snyk Intellij Plugin"
        every { pluginInfo.integrationVersion } returns "2.4.61"
        every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
        every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"

        cut = LanguageServerWrapper("dummy")
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
        every { lsMock.initialize(any<InitializeParams>()) } returns CompletableFuture.completedFuture(null)
        justRun { lsMock.initialized(any()) }

        cut.sendInitializeMessage()

        verify { lsMock.initialize(any<InitializeParams>()) }
        verify { lsMock.initialized(any()) }
        verify { folderConfigSettingsMock.getAll() }
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

        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

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
                        ),
                ),
            ),
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
        }
    }

    @Test
    fun `sendScanCommand waits for smart mode`() {
        simulateRunningLS()
        justRun { dumbServiceMock.runWhenSmart(any()) }

        cut.sendScanCommand(projectMock)

        verify { dumbServiceMock.runWhenSmart(any()) }
    }

    @Test
    fun `sendScanCommand only runs when initialized`() {
        cut.isInitialized = false
        cut.sendScanCommand(projectMock)

        verify(exactly = 0) { dumbServiceMock.runWhenSmart(any()) }
    }

    @Test
    fun `sendScanCommand only runs when not disposed`() {
        every { applicationMock.isDisposed } returns true

        cut.sendScanCommand(projectMock)

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

        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        val folder = "testFolder"
        cut.configuredWorkspaceFolders.add(WorkspaceFolder(Paths.get(folder).toUri().toASCIIString(), folder))

        cut.sendFolderScanCommand(folder, projectMock)

        verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }

    @Test
    fun `sendFolderScanCommand does not trigger LS when run and in dumb mode`() {
        simulateRunningLS()
        every { dumbServiceMock.isDumb } returns true

        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        cut.sendFolderScanCommand("testFolder", projectMock)

        verify(exactly = 0) { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }

    @Test
    fun `updateConfiguration should not run when LS not initialized`() {
        cut.isInitialized = false
        cut.updateConfiguration()

        verify(exactly = 0) { lsMock.workspaceService.didChangeConfiguration(any<DidChangeConfigurationParams>()) }
    }

    @Test
    fun `updateConfiguration should not run when disposed`() {
        every { applicationMock.isDisposed } returns true
        simulateRunningLS()

        cut.updateConfiguration()

        verify(exactly = 0) { lsMock.workspaceService.didChangeConfiguration(any<DidChangeConfigurationParams>()) }
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

        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        cut.sendFolderScanCommand("testFolder", projectMock)

        verify(exactly = 0) { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }

    private fun simulateRunningLS() {
        cut.languageClient = mockk(relaxed = true)
        val processMock = mockk<Process>(relaxed = true)
        cut.process = processMock
        every { processMock.info().startInstant().isPresent } returns true
        every { processMock.isAlive } returns true
    }

    @Test
    fun getInitializationOptions() {
        settings.token = "testToken"
        settings.customEndpointUrl = "testEndpoint/"
        settings.ignoreUnknownCA = true
        settings.cliPath = "testCliPath"
        settings.organization = "org"

        val actual = cut.getSettings()

        assertEquals(settings.snykCodeQualityIssuesScanEnable.toString(), actual.activateSnykCodeQuality)
        assertEquals(settings.snykCodeSecurityIssuesScanEnable.toString(), actual.activateSnykCodeSecurity)
        assertEquals(settings.iacScanEnabled.toString(), actual.activateSnykIac)
        assertEquals(settings.ossScanEnable.toString(), actual.activateSnykOpenSource)
        assertEquals(settings.token, actual.token)
        assertEquals("${settings.ignoreUnknownCA}", actual.insecure)
        assertEquals(getCliFile().absolutePath, actual.cliPath)
        assertEquals(settings.organization, actual.organization)
        assertEquals(settings.isDeltaFindingsEnabled().toString(), actual.enableDeltaFindings)
    }
}
