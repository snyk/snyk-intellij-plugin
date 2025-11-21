@file:Suppress("UNCHECKED_CAST")

package io.snyk.plugin.ui.toolwindow

import com.intellij.ide.util.gotoByName.GotoFileCellRenderer
import com.intellij.openapi.components.service
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.tree.TreeUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.setupDummyCliFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykErrorPanel
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import snyk.common.SnykError
import snyk.common.UIComponentFinder.getComponentByName
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.PresentableError
import snyk.common.lsp.SnykScanParams
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.awt.Component
import javax.swing.JEditorPane
import javax.swing.JTextArea
import kotlin.reflect.KClass

@RunWith(JUnit4::class)
@Ignore("Too unstable in CI")
class SnykToolWindowPanelIntegTest : HeavyPlatformTestCase() {
        private lateinit var toolWindowPanel: SnykToolWindowPanel

    private val scanPublisherLS
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)!!

    private val fakeApiToken = "fake_token"
    private val lsMock = mockk<LanguageServer>(relaxed = true)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        mockkStatic("snyk.trust.TrustedProjectsKt")
        val settings = pluginSettings()
        settings.token = fakeApiToken // needed to avoid forced Auth panel showing
        settings.pluginFirstRun = false
        settings.cliReleaseChannel = "preview"

        // Disable all scan types. Individual tests should enable any they need.
        settings.ossScanEnable = false
        settings.iacScanEnabled = false
        settings.snykCodeSecurityIssuesScanEnable = false

        // ToolWindow need to be reinitialised for every test as Project is recreated for Heavy tests
        // also we MUST do it *before* any actual test code due to initialisation of SnykScanListener in init{}
        toolWindowPanel = project.service()
        setupDummyCliFile()
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true
        mockkStatic(GotoFileCellRenderer::class)
        every { GotoFileCellRenderer.getRelativePath(any(), any()) } returns "abc/"
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        languageServerWrapper.isInitialized = true
        languageServerWrapper.languageServer = lsMock
        languageServerWrapper.process = mockk(relaxed = true)
        languageServerWrapper.languageClient = mockk(relaxed = true)
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        try {
            super.tearDown()
        } catch (_: Exception) {
            // nothing to do here
        }
    }

    private fun setUpOssTest() { pluginSettings().ossScanEnable = true }


    // TODO - Agree on the correct UX, and either reinstate this test case or update it to reflect the current UX.
    @Ignore("IDE shows generic error when no IaC support file found")
    @Test
    fun `test when no IAC supported file found should display special text (not error) in node and description`() {
        mockkObject(SnykBalloonNotificationHelper)

        val snykError =
            SnykScanParams(
                "failed",
                "iac",
                project.basePath!!,
                PresentableError(
                    error = SnykToolWindowPanel.NO_IAC_FILES,
                    showNotification = true
                )
            )
        val snykErrorControl = SnykScanParams(
            "failed",
            "iac",
            project.basePath!!,
            PresentableError(
                error = "control",
                showNotification = true
            )
        )

        scanPublisherLS.scanningError(snykErrorControl)
        scanPublisherLS.scanningError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val rootIacTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        // flow and internal state check
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(any(), project)
        }
        assertTrue(getSnykCachedResults(project)?.currentIacError == null)
        assertTrue(getSnykCachedResults(project)?.currentIacResultsLS?.isEmpty() ?: false)
        // node check
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + SnykToolWindowPanel.NO_SUPPORTED_PACKAGE_MANAGER_FOUND,
            rootIacTreeNode.userObject
        )
        // description check
        TreeUtil.selectNode(toolWindowPanel.getTree(), rootIacTreeNode)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
        val jEditorPane = getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class
        )
        assertNotNull(jEditorPane)
        jEditorPane!!
        assertTrue(jEditorPane.text.contains(SnykToolWindowPanel.NO_OSS_FILES))
    }

    // TODO - Agree on the correct UX, and either reinstate this test case or update it to reflect the current UX.
    @Ignore("IDE shows generic error when no OSS support file found")
    @Test
    fun `test when no OSS supported file found should display special text (not error) in node and description`() {
        mockkObject(SnykBalloonNotificationHelper)
        setUpOssTest()

        val noFilesErrorParams =
            SnykScanParams(
                "failed",
                "oss",
                project.basePath!!,
                PresentableError(
                    error = SnykToolWindowPanel.NO_OSS_FILES,
                    showNotification = true
                )
            )
        val controlErrorParams =
            SnykScanParams(
                "failed",
                "oss",
                project.basePath!!,
                PresentableError(
                    error = "control",
                    showNotification = true
                )
            )

        scanPublisherLS.scanningError(noFilesErrorParams)
        scanPublisherLS.scanningError(controlErrorParams)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val rootOssTreeNode = toolWindowPanel.getRootOssIssuesTreeNode()

        waitWhileTreeBusy()

        // flow and internal state check
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(any(), project)
        }

        val ossError = getSnykCachedResults(project)?.currentOssError
        assertNotNull(ossError)
        assertTrue(getSnykCachedResults(project)?.currentOSSResultsLS?.isEmpty() ?: false)
        val cliErrorMessage = rootOssTreeNode.originalCliErrorMessage
        assertTrue(cliErrorMessage != null && cliErrorMessage.startsWith(SnykToolWindowPanel.NO_OSS_FILES))
        // node check
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + SnykToolWindowPanel.NO_SUPPORTED_PACKAGE_MANAGER_FOUND,
            rootOssTreeNode.userObject
        )
        // description check
        TreeUtil.selectNode(toolWindowPanel.getTree(), rootOssTreeNode)

        val jEditorPane = getNonNullDescriptionComponent(JEditorPane::class)
        assertTrue(jEditorPane.text.contains(SnykToolWindowPanel.NO_OSS_FILES))
    }

    // TODO - Agree on the correct UX, and either reinstate this test case or update it to reflect the current UX.
    @Ignore("IDE presents notification, but does not redirect to auth panel")
    @Test
    fun `test OSS scan should redirect to Auth panel if token is invalid`() {
        mockkObject(SnykBalloonNotificationHelper)

        val authErrorParams =
            SnykScanParams(
                "failed",
                "oss",
                project.basePath!!,
                PresentableError(
                    error = "auth error",
                    showNotification = true
                )
            )
        val controlErrorParams =
            SnykScanParams(
                "failed",
                "oss",
                project.basePath!!,
                PresentableError(
                    error = "control",
                    showNotification = true
                )
            )

        scanPublisherLS.scanningError(controlErrorParams)
        scanPublisherLS.scanningError(authErrorParams)

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(controlErrorParams.presentableError?.error!!, project)
        }
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(authErrorParams.presentableError?.error!!, project)
        }

        assertNull(getSnykCachedResults(project)?.currentOssError)

        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + " (error)",
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )

        getNonNullDescriptionComponent(SnykAuthPanel::class)
    }

    @Test
    fun `test should display '(error)' in OSS root tree node when result is empty and error occurs`() {
        val ossError = SnykError("an error", project.basePath!!)
        val presentableError = PresentableError(
            error = ossError.message,
            path = ossError.path,
            showNotification = true
        )
        val ossErrorParams = SnykScanParams(
            "failed",
            "oss",
            ossError.path,
            presentableError
        )

        scanPublisherLS.scanningError(ossErrorParams)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(presentableError, getSnykCachedResults(project)?.currentOssError)
        assertTrue(getSnykCachedResults(project)?.currentOSSResultsLS?.isEmpty() ?: false)
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + " (error)",
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )
    }

    @Test
    fun `test should display 'scanning' in OSS root tree node when it is scanning`() {
        setUpOssTest()

        mockkStatic("io.snyk.plugin.UtilsKt")
        every { isOssRunning(project) } returns true
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        toolWindowPanel.updateTreeRootNodesPresentation(null, 0, 0)
        assertTrue(getSnykCachedResults(project)?.currentOSSResultsLS?.isEmpty() ?: false)
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + " (scanning...)",
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )
    }

    @Test
    fun testIacErrorShown() {
        mockkObject(SnykBalloonNotificationHelper)

        // mock IaC results
        val iacError = SnykError("fake error", "fake path")
        val presentableError = PresentableError(
            error = iacError.message,
            path = iacError.path,
            showNotification = true
        )
        val iacErrorParams = SnykScanParams(
            "failed",
            "iac",
            iacError.path,
            presentableError
        )

        // Trigger the callback from Language Server signalling scan failure
        scanPublisherLS.scanningError(iacErrorParams)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify (exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(iacError.message, project)
        }
        assertEquals(presentableError, getSnykCachedResults(project)?.currentIacError)

        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootIacIssuesTreeNode())

        val errorPanel = getNonNullDescriptionComponent(SnykErrorPanel::class)
        val errorMessageTextArea = getComponentByName(errorPanel, JTextArea::class, "errorMessageTextArea")
        val pathTextArea = getComponentByName(errorPanel, JTextArea::class, "pathTextArea")
        assertEquals(iacError.message, errorMessageTextArea!!.text)
        assertEquals(iacError.path, pathTextArea!!.text)
    }

    @Test
    fun `test all root nodes are shown`() {
        waitWhileTreeBusy()

        val rootNode = toolWindowPanel.getRootNode()
        setOf(
            SnykToolWindowPanel.OSS_ROOT_TEXT,
            SnykToolWindowPanel.CODE_SECURITY_ROOT_TEXT,
            SnykToolWindowPanel.IAC_ROOT_TEXT,
        ).forEach {
            assertNotNull(
                "Root node for [$it] not found",
                TreeUtil.findNodeWithObject(rootNode, it)
            )
        }
    }

    private fun waitWhileTreeBusy() = PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

    private fun <T: Component> getNonNullDescriptionComponent(clazz: KClass<T>, name: String? = null): T {
        // Check that all relevant UI work has completed before we access the description panel
        waitWhileTreeBusy()
        PlatformTestUtil.waitWhileBusy { toolWindowPanel.getDescriptionPanel().components.isEmpty() }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        // Look for a component of the correct type and name within the description panel
        val component = getComponentByName(toolWindowPanel.getDescriptionPanel(), clazz, name)
        assertNotNull(component)
        return component!!
    }
}
