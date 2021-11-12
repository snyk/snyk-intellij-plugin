package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExpandableItemsHandlerFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import io.snyk.plugin.SnykBaseTest
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykApiService
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.services.download.SnykCliDownloaderService
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.UIComponentFinder
import snyk.amplitude.AmplitudeExperimentService
import java.awt.Container
import javax.swing.JTree

class SnykToolWindowPanelTest : SnykBaseTest() {
    private val analyticsService: SnykAnalyticsService = replaceServiceWithMock(true)
    private val taskQueueService: SnykTaskQueueService = replaceServiceWithMock(true)
    private val snykApiServiceMock: SnykApiService = replaceServiceWithMock(true)
    private val settings: SnykApplicationSettingsStateService = replaceServiceWithMock(true)
    private val amplitudeExperimentationServiceMock: AmplitudeExperimentService = replaceServiceWithMock()

    private lateinit var cut: SnykToolWindowPanel

    @Before
    override fun setUp() {
        super.setUp()

        every { settings.token } returns null
        every { settings.sastOnServerEnabled } returns true
        every { snykApiServiceMock.sastOnServerEnabled } returns true

        /*
        Mock the tree construction with spys, reusing functionality if possible.
        If we want to test tree interactions, this MUST be changed/removed, but that
        should be an integration or a UI test anyway
        */
        stubForTreeConstruction()

        replaceServiceWithMock<SnykCliDownloaderService>(true)
    }

    private fun stubForTreeConstruction() {
        mockkStatic(ExpandableItemsHandlerFactory::class)
        mockkStatic(TreeSpeedSearch::class)
        mockkStatic(ActionManager::class)
        val actionManager = spyk<ActionManager>()
        every { ActionManager.getInstance() } returns actionManager
        every { actionManager.getAction(any()) } returns spyk<ActionGroup>()
        val actionToolbar = spyk<ActionToolbar>()
        every { actionManager.createActionToolbar(any(), any(), any()) } returns actionToolbar
        every { actionToolbar.component } returns spyk()
        every { ExpandableItemsHandlerFactory.install(any<JTree>()) } returns spyk()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkAll()
    }

    @Test
    fun `should display experimental auth panel if user in test group`() {
        every { amplitudeExperimentationServiceMock.isPartOfExperimentalWelcomeWorkflow() } returns true
        every { settings.pluginFirstRun } returns true

        cut = SnykToolWindowPanel(project)

        val vulnerabilityTree = cut.vulnerabilitiesTree
        val authPanel = UIComponentFinder.getJPanelByName(cut, "authPanel")
        assertNotNull(authPanel)
        assertEquals(findOnePixelSplitter(vulnerabilityTree), authPanel!!.parent)
    }

    @Test
    fun `should not display experimental auth panel if user in control group`() {
        every { amplitudeExperimentationServiceMock.isPartOfExperimentalWelcomeWorkflow() } returns false
        every { settings.pluginFirstRun } returns true

        cut = SnykToolWindowPanel(project)

        val authPanel = UIComponentFinder.getJPanelByName(cut, "authPanel")
        assertEquals(CenterOneComponentPanel::class, authPanel!!.parent::class)
    }

    @Test
    fun `should not display onboarding panel and run scan directly if if user in test group`() {
        every { amplitudeExperimentationServiceMock.isPartOfExperimentalWelcomeWorkflow() } returns true
        every { settings.token } returns "test-token"
        every { settings.pluginFirstRun } returns true
        justRun { taskQueueService.scan() }

        cut = SnykToolWindowPanel(project)

        val vulnerabilityTree = cut.vulnerabilitiesTree
        val descriptionPanel = UIComponentFinder.getJPanelByName(cut, "descriptionPanel")
        assertNotNull(descriptionPanel)
        assertEquals(findOnePixelSplitter(vulnerabilityTree), descriptionPanel!!.parent)

        verify(exactly = 1) { taskQueueService.scan() }
        verify(exactly = 1) { analyticsService.logAnalysisIsTriggered(any()) }
    }

    @Test
    fun `should not display experimental onboarding panel if user in control group`() {
        every { amplitudeExperimentationServiceMock.isPartOfExperimentalWelcomeWorkflow() } returns false
        every { settings.pluginFirstRun } returns true
        cut = SnykToolWindowPanel(project)

        cut.displayPluginFirstRunPanel()

        val onboardingPanel = UIComponentFinder.getJPanelByName(cut, "onboardingPanel")
        assertNotNull(onboardingPanel)
        assertEquals(CenterOneComponentPanel::class, onboardingPanel!!.parent::class)
    }

    @Test
    fun `should automatically enable products if paid test group user`() {
        every { amplitudeExperimentationServiceMock.isPartOfExperimentalWelcomeWorkflow() } returns true
        every { settings.token } returns "test-token"
        every { settings.pluginFirstRun } returns true

        mockkStatic(Registry::class)
        every { Registry.`is`("snyk.preview.iac.enabled", false) } returns true

        SnykToolWindowPanel(project)

        verify(exactly = 1) {
            snykApiServiceMock.sastOnServerEnabled
            settings.sastOnServerEnabled = true
            settings.iacScanEnabled = true
            settings.advisorEnable = true
            settings.ossScanEnable = true
            settings.snykCodeSecurityIssuesScanEnable = true
            settings.snykCodeQualityIssuesScanEnable = true
            settings.pluginFirstRun = false
        }
        unmockkStatic(Registry::class)
    }

    private fun findOnePixelSplitter(vulnerabilityTree: Tree): Container? {
        var currentParent = vulnerabilityTree.parent
        while (currentParent != null && currentParent::class != OnePixelSplitter::class) {
            currentParent = currentParent.parent
        }
        return currentParent
    }
}
