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
import io.mockk.verify
import io.snyk.plugin.net.CliConfigSettings
import io.snyk.plugin.net.LocalCodeEngine
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykApiService
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykTaskQueueService
import org.junit.Test
import snyk.UIComponentFinder
import snyk.amplitude.AmplitudeExperimentService
import java.awt.Container

class SnykToolWindowPanelTest : LightPlatform4TestCase() {
    private val analyticsService: SnykAnalyticsService = mockk(relaxed = true)
    private val taskQueueService = mockk<SnykTaskQueueService>(relaxed = true)
    private val snykApiServiceMock = mockk<SnykApiService>()
    private val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
    private val amplitudeExperimentationServiceMock = mockk<AmplitudeExperimentService>()
    private lateinit var cut: SnykToolWindowPanel

    override fun setUp() {
        super.setUp()
        unmockkAll()

        val application = ApplicationManager.getApplication()
        application.replaceService(SnykApplicationSettingsStateService::class.java, settings, application)
        application.replaceService(SnykApiService::class.java, snykApiServiceMock, application)
        application.replaceService(SnykAnalyticsService::class.java, analyticsService, application)
        application.replaceService(
            AmplitudeExperimentService::class.java,
            amplitudeExperimentationServiceMock,
            application
        )

        project.replaceService(SnykTaskQueueService::class.java, taskQueueService, project)

        every { settings.token } returns null
        every { settings.sastOnServerEnabled } returns true
        every { settings.localCodeEngineEnabled } returns false
        every { snykApiServiceMock.getSastSettings()?.sastEnabled } returns true
        every { snykApiServiceMock.getSastSettings()?.localCodeEngine?.enabled } returns false
    }

    override fun tearDown() {
        unmockkAll()

        val application = ApplicationManager.getApplication()
        application.replaceService(AmplitudeExperimentService::class.java, AmplitudeExperimentService(), application)
        application.replaceService(SnykApplicationSettingsStateService::class.java, SnykApplicationSettingsStateService(), application)
        application.replaceService(SnykApiService::class.java, SnykApiService(), application)
        application.replaceService(SnykAnalyticsService::class.java, SnykAnalyticsService(), application)

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
    }

    @Test
    fun `should not display onboarding panel and run scan directly`() {
        every { settings.token } returns "test-token"
        every { settings.pluginFirstRun } returns true
        every { snykApiServiceMock.getSastSettings(any()) } returns CliConfigSettings(
            true,
            LocalCodeEngine(false),
            false
        )
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
    fun `should automatically enable all products on first run after Auth`() {
        every { snykApiServiceMock.getSastSettings(any()) } returns CliConfigSettings(
            true,
            LocalCodeEngine(false),
            false
        )
        val application = ApplicationManager.getApplication()
        application.replaceService(
            SnykApplicationSettingsStateService::class.java,
            SnykApplicationSettingsStateService(),
            application
        )
        pluginSettings().token = "test-token"
        pluginSettings().pluginFirstRun = true

        SnykToolWindowPanel(project)

        verify(exactly = 1, timeout = 5000) {
            snykApiServiceMock.getSastSettings()
        }
        assertTrue(pluginSettings().ossScanEnable)
        assertTrue(pluginSettings().snykCodeSecurityIssuesScanEnable)
        assertTrue(pluginSettings().snykCodeQualityIssuesScanEnable)
        assertTrue(pluginSettings().iacScanEnabled)
        assertTrue(pluginSettings().containerScanEnabled)
    }

    private fun findOnePixelSplitter(vulnerabilityTree: Tree): Container? {
        var currentParent = vulnerabilityTree.parent
        while (currentParent != null && currentParent::class != OnePixelSplitter::class) {
            currentParent = currentParent.parent
        }
        return currentParent
    }
}
