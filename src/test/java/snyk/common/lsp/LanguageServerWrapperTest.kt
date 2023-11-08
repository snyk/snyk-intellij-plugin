package snyk.common.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.pluginInfo
import java.util.concurrent.CompletableFuture

class LanguageServerWrapperTest {

    private val projectMock: Project = mockk()
    private val lsMock: LanguageServer = mockk()
    private lateinit var cut: LanguageServerWrapper

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { pluginSettings() } returns SnykApplicationSettingsStateService()
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

        cut.sendInitializeMessage(projectMock)

        verify { lsMock.initialize(any<InitializeParams>()) }
    }

    @Test
    fun `sendReportAnalyticsCommand should send a reportAnalytics command to the language server`() {
        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        cut.sendReportAnalyticsCommand(mockk(relaxed = true))

        verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }
}
