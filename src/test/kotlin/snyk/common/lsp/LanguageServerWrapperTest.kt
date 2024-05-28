package snyk.common.lsp

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
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
import junit.framework.TestCase.assertEquals
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService
import java.util.concurrent.CompletableFuture

class LanguageServerWrapperTest {

    private val applicationMock: Application = mockk()
    private val projectMock: Project = mockk()
    private val lsMock: LanguageServer = mockk()
    private val settings = SnykApplicationSettingsStateService()
    private val trustServiceMock = mockk<WorkspaceTrustService>(relaxed = true)

    private lateinit var cut: LanguageServerWrapper

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns applicationMock
        every { applicationMock.getService(WorkspaceTrustService::class.java) } returns trustServiceMock

        val projectManagerMock = mockk<ProjectManager>()
        every { applicationMock.getService(ProjectManager::class.java) } returns projectManagerMock
        every { projectManagerMock.openProjects } returns arrayOf(projectMock)

        every { pluginSettings() } returns settings
        mockkStatic("snyk.PluginInformationKt")
        every { pluginInfo } returns mockk(relaxed = true)
        every { pluginInfo.integrationName } returns "Snyk Intellij Plugin"
        every { pluginInfo.integrationVersion } returns "2.4.61"
        every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
        every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"

        cut = LanguageServerWrapper("dummy")
        cut.languageServer = lsMock
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `sendInitializeMessage should send an initialize message to the language server`() {
        val rootManagerMock = mockk<ProjectRootManager>(relaxed = true)
        every { projectMock.getService(ProjectRootManager::class.java) } returns rootManagerMock
        every { rootManagerMock.contentRoots } returns emptyArray()
        every { lsMock.initialize(any<InitializeParams>()) } returns CompletableFuture.completedFuture(null)
        justRun { lsMock.initialized(any()) }

        cut.sendInitializeMessage()

        verify { lsMock.initialize(any<InitializeParams>()) }
        verify { lsMock.initialized(any()) }
    }

    @Test
    fun `sendReportAnalyticsCommand should send a reportAnalytics command to the language server`() {
        cut.languageClient = mockk(relaxed = true)
        val processMock = mockk<Process>(relaxed = true)
        cut.process = processMock
        every { processMock.info().startInstant().isPresent } returns true
        every { processMock.isAlive } returns true
        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        cut.sendReportAnalyticsCommand(mockk(relaxed = true))

        verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }

    @Test
    fun getInitializationOptions() {
        settings.token = "testToken"
        settings.customEndpointUrl = "testEndpoint/"
        settings.ignoreUnknownCA = true
        settings.cliPath = "testCliPath"

        val actual = cut.getSettings()

        assertEquals("false", actual.activateSnykCode)
        assertEquals("false", actual.activateSnykIac)
        assertEquals("false", actual.activateSnykOpenSource)
        assertEquals(settings.token, actual.token)
        assertEquals("${settings.ignoreUnknownCA}", actual.insecure)
        assertEquals(getCliFile().absolutePath, actual.cliPath)
    }

    @Ignore // somehow it doesn't work in the pipeline
    @Test
    fun `sendFeatureFlagCommand should return true if feature flag is enabled`() {
        // Arrange
        cut.languageClient = mockk(relaxed = true)
        val featureFlag = "testFeatureFlag"
        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(mapOf("ok" to true))
        justRun { applicationMock.invokeLater(any()) }

        // Act
        val result = cut.getFeatureFlagStatus(featureFlag)

        // Assert
        assertEquals(true, result)
    }
}
