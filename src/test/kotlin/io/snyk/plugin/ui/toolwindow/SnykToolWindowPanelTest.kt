package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.treeStructure.Tree
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykTaskQueueService
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.WorkspaceService
import org.junit.Ignore
import org.junit.Test
import snyk.UIComponentFinder
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.SnykLanguageClient
import java.awt.Container
import java.util.concurrent.CompletableFuture

class SnykToolWindowPanelTest : LightPlatform4TestCase() {
    private val taskQueueService = mockk<SnykTaskQueueService>(relaxed = true)
    private val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
    private lateinit var cut: SnykToolWindowPanel
    val lsMock = mockk<LanguageServer>()
    private val lsClientMock = mockk<SnykLanguageClient>()
    private val lsProcessMock = mockk<Process>()
    private val workspaceServiceMock = mockk<WorkspaceService>()

    override fun setUp() {
        super.setUp()
        unmockkAll()

        val application = ApplicationManager.getApplication()
        application.replaceService(SnykApplicationSettingsStateService::class.java, settings, application)

        project.replaceService(SnykTaskQueueService::class.java, taskQueueService, project)

        val lsw = LanguageServerWrapper.getInstance(project)
        lsw.languageServer = lsMock
        lsw.languageClient = lsClientMock
        lsw.process = lsProcessMock
        lsw.isInitialized = true

        every { lsProcessMock.info().startInstant().isPresent } returns true
        every { lsProcessMock.isAlive } returns true
        every { lsMock.workspaceService } returns workspaceServiceMock
        val sastSettings = mapOf(
            Pair("sastEnabled", true),
            Pair(
                "localCodeEngine", mapOf(
                    Pair("allowCloudUpload", false),
                    Pair("enabled", false),
                    Pair("url", "")
                )
            ),
            Pair("org", "1234"),
            Pair("reportFalsePositivesEnabled", false),
            Pair("autofixEnabled", false),
            Pair("supportedLanguages", emptyList<String>()),
        )
        every { workspaceServiceMock.executeCommand(any()) } returns CompletableFuture.completedFuture(sastSettings)

        every { settings.token } returns null
        every { settings.sastOnServerEnabled } returns true
        every { settings.localCodeEngineEnabled } returns false
    }

    override fun tearDown() {
        unmockkAll()

        val application = ApplicationManager.getApplication()
        application.replaceService(
            SnykApplicationSettingsStateService::class.java,
            SnykApplicationSettingsStateService(),
            application
        )

        project.replaceService(SnykTaskQueueService::class.java, SnykTaskQueueService(project), project)
        super.tearDown()
    }

    @Test
    fun `should display auth panel `() {
        every { settings.pluginFirstRun } returns true

        cut = SnykToolWindowPanel(project)

        val authPanel = UIComponentFinder.getJPanelByName(cut, "authPanel")
        assertNotNull(authPanel)
        val treePanel = UIComponentFinder.getJPanelByName(cut, "treePanel")
        assertNotNull(treePanel)
        val summaryPanel = UIComponentFinder.getJPanelByName(cut, "summaryPanel")
        assertNotNull(summaryPanel)
    }

    @Test
    fun `should not display onboarding panel and run scan directly`() {
        every { settings.token } returns "test-token"
        every { settings.pluginFirstRun } returns true
        justRun { taskQueueService.scan() }

        cut = SnykToolWindowPanel(project)

        val vulnerabilityTree = cut.vulnerabilitiesTree
        val descriptionPanel = UIComponentFinder.getJPanelByName(cut, "descriptionPanel")
        assertNotNull(descriptionPanel)
        assertEquals(findOnePixelSplitter(vulnerabilityTree)?.parent, descriptionPanel!!.parent)
    }

    //TODO rewrite
    @Ignore("change to language server")
    @Test
    fun `should automatically enable all products on first run after Auth`() {
        val application = ApplicationManager.getApplication()
        application.replaceService(
            SnykApplicationSettingsStateService::class.java,
            SnykApplicationSettingsStateService(),
            application
        )
        pluginSettings().token = "test-token"
        pluginSettings().pluginFirstRun = true

        SnykToolWindowPanel(project)

        assertTrue(pluginSettings().ossScanEnable)
        assertTrue(pluginSettings().snykCodeSecurityIssuesScanEnable)
        assertTrue(pluginSettings().iacScanEnabled)
    }

    @Test
    fun `should automatically enable all products on first run after Auth, with local engine enabled`() {
        val application = ApplicationManager.getApplication()
        val settings = SnykApplicationSettingsStateService()
        application.replaceService(
            SnykApplicationSettingsStateService::class.java,
            settings,
            application
        )
        settings.token = "test-token"
        settings.pluginFirstRun = true
        settings.cliReleaseChannel = "preview"

        assertTrue(LanguageServerWrapper.getInstance(project).ensureLanguageServerInitialized())

        SnykToolWindowPanel(project)

        assertTrue(pluginSettings().ossScanEnable)
        assertTrue(pluginSettings().snykCodeSecurityIssuesScanEnable)
        assertTrue(pluginSettings().iacScanEnabled)
    }

    private fun findOnePixelSplitter(vulnerabilityTree: Tree): Container? {
        var currentParent = vulnerabilityTree.parent
        while (currentParent != null && currentParent::class != OnePixelSplitter::class) {
            currentParent = currentParent.parent
        }
        return currentParent
    }
}
