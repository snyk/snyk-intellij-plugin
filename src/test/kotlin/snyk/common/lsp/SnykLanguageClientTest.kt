package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.Called
import io.mockk.MockKStubScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService
import kotlin.io.path.Path

import com.intellij.openapi.diagnostic.Logger

class SnykLanguageClientTest {
    private lateinit var cut: SnykLanguageClient
    private var snykPluginDisposable = SnykPluginDisposable()

    private val applicationMock: Application = mockk()
    private val projectMock: Project = mockk()
    private val settings = SnykApplicationSettingsStateService()
    private val trustServiceMock = mockk<WorkspaceTrustService>(relaxed = true)
    private val dumbServiceMock = mockk<DumbService>()
    private val projectManagerMock = mockk<ProjectManager>()
    private val loggerMock = mockk<Logger>()

    private val mockProject = mockk<Project>()
    private val mockScanPublisher = mockk<SnykScanListenerLS>()
    private val mockVirtualFile = mockk<VirtualFile>()

    private val filePath = "some/file.txt"

    private val diagnosticsParams = mockk<PublishDiagnosticsParams>()
    private val diagnostic = mockk<Diagnostic>()

    private val issueList = listOf(mockk<ScanIssue>())

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic(ApplicationManager::class)

        every { ApplicationManager.getApplication() } returns applicationMock
        every { applicationMock.getService(WorkspaceTrustService::class.java) } returns trustServiceMock

        every { applicationMock.getService(ProjectManager::class.java) } returns projectManagerMock
        every { applicationMock.getService(SnykPluginDisposable::class.java) } returns snykPluginDisposable
        every { applicationMock.isDisposed } returns false
        every { applicationMock.messageBus } returns mockk(relaxed = true)

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

        mockkObject(filePath)
        every { filePath.toVirtualFile() } returns mockVirtualFile
        every { diagnostic.source } returns "Snyk Error"
        every { diagnosticsParams.diagnostics } returns listOf(diagnostic)


        snykPluginDisposable = SnykPluginDisposable()

        cut = SnykLanguageClient()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun applyEdit() {
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
        val mockIssue = mockk<ScanIssue>()
        val param = SnykScanParams("success", "code", "testFolder", listOf(mockIssue))

        cut.snykScan(param)

        // you cannot display / forward the issues without mapping them to open projects
        verify (exactly = 0) { projectManagerMock.openProjects }
    }

    @Test
    fun `hasAuthenticated does not run when disposed`(){
        every { applicationMock.isDisposed } returns true

        val unexpected = "abc"
        cut.hasAuthenticated(HasAuthenticatedParam(unexpected))

        assertNotEquals(unexpected, settings.token)
    }

    @Test
    fun `addTrustedPaths should not run when disposed`() {
        every { applicationMock.isDisposed } returns true

        val unexpected = "abc"
        cut.addTrustedPaths(SnykTrustedFoldersParams(listOf(unexpected)))

        verify (exactly = 0){ applicationMock.getService(WorkspaceTrustService::class.java) }
    }

    @Test
    fun `addTrustedPaths should add path to trusted paths`() {
        val path = "abc"
        cut.addTrustedPaths(SnykTrustedFoldersParams(listOf(path)))

        verify { trustServiceMock.addTrustedPath(eq(Path(path))) }
    }

    @Test
    fun `should clear cache for empty diagnostics`() {
        cut.updateCache(diagnosticsParams)

        verify { mockScanPublisher.onPublishDiagnostics("code", any(), emptyList()) }
        verify { mockScanPublisher.onPublishDiagnostics("oss", any(), emptyList()) }
    }


    @Test
    fun `should handle Snyk Error diagnostics`() {
        cut.updateCache(diagnosticsParams)

        verify { mockScanPublisher.onPublishDiagnostics(any(), any(), any()) wasNot Called }
        verify { cut.handleDiagnosticError(mockProject, diagnosticsParams) }
    }

    @Test
    fun `should publish diagnostics for supported products`() {
        val supportedProducts = listOf("Snyk Open Source", "Snyk Code")

        for (product in supportedProducts) {
            every { Gson().fromJson(issueList.toString(), ScanIssue::class.java) } returns issueList.first()
            cut.updateCache(diagnosticsParams)
            verify { mockScanPublisher.onPublishDiagnostics(product, any(), issueList) }
        }
    }


    @Test
    fun `should handle exceptions during publishing`() {
        val exception = RuntimeException("Test Exception")
        every { cut.getScanPublishersFor(any()) } throws exception

        cut.updateCache(diagnosticsParams)

        verify { loggerMock.error("Error publishing the new diagnostics", exception) }
    }
}

private infix fun <T, B> MockKStubScope<T, B>.returns(first: ScanIssue): B {
    TODO("Not yet implemented")
}

