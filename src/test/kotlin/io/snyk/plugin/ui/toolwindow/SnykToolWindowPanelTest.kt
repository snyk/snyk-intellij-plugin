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

        ApplicationManager.getApplication()
            .replaceService(SnykApplicationSettingsStateService::class.java, settings, project)
        ApplicationManager.getApplication()
            .replaceService(SnykApiService::class.java, snykApiServiceMock, project)
        ApplicationManager.getApplication()
            .replaceService(SnykTaskQueueService::class.java, taskQueueService, project)
        ApplicationManager.getApplication()
            .replaceService(SnykAnalyticsService::class.java, analyticsService, project)
        ApplicationManager.getApplication()
            .replaceService(AmplitudeExperimentService::class.java, amplitudeExperimentationServiceMock, project)

        project.replaceService(AmplitudeExperimentService::class.java, amplitudeExperimentationServiceMock, project)
        project.replaceService(SnykTaskQueueService::class.java, taskQueueService, project)
        project.replaceService(SnykApplicationSettingsStateService::class.java, settings, project)
        project.replaceService(SnykAnalyticsService::class.java, analyticsService, project)
        project.replaceService(SnykApiService::class.java, snykApiServiceMock, project)

        every { settings.token } returns null
        every { settings.sastOnServerEnabled } returns true
        every { snykApiServiceMock.sastOnServerEnabled } returns true
    }

    override fun tearDown() {
        try {
            unmockkAll()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `should display auth panel `() {
        every { settings.pluginFirstRun } returns true

        cut = SnykToolWindowPanel(project)

        val vulnerabilityTree = cut.vulnerabilitiesTree
        val authPanel = UIComponentFinder.getJPanelByName(cut, "authPanel")
        assertNotNull(authPanel)
        assertEquals(findOnePixelSplitter(vulnerabilityTree), authPanel!!.parent)
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
        assertEquals(findOnePixelSplitter(vulnerabilityTree), descriptionPanel!!.parent)

        verify(exactly = 1) { taskQueueService.scan() }
        verify(exactly = 1) { analyticsService.logAnalysisIsTriggered(any()) }
    }

    @Test
    fun `should automatically enable products`() {
        every { settings.token } returns "test-token"
        every { settings.pluginFirstRun } returns true

        SnykToolWindowPanel(project)

        verify(exactly = 1) {
            snykApiServiceMock.sastOnServerEnabled
            settings.sastOnServerEnabled = true
            settings.iacScanEnabled = true
            settings.containerScanEnabled = true
            settings.snykCodeSecurityIssuesScanEnable = true
            settings.snykCodeQualityIssuesScanEnable = true
        }
    }

    private fun findOnePixelSplitter(vulnerabilityTree: Tree): Container? {
        var currentParent = vulnerabilityTree.parent
        while (currentParent != null && currentParent::class != OnePixelSplitter::class) {
            currentParent = currentParent.parent
        }
        return currentParent
    }
}
