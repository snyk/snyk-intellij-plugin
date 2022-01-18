package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBus
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.events.SnykSettingsListener.Companion.SNYK_SETTINGS_TOPIC
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykCliAuthenticationService
import io.snyk.plugin.services.download.SnykCliDownloaderService
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.UIComponentFinder
import snyk.amplitude.AmplitudeExperimentService

class SnykAuthPanelTest {
    private val project = mockk<Project>()
    private val analyticsService = mockk<SnykAnalyticsService>()

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic(ApplicationManager::class)
        val application = mockk<Application>()
        every { ApplicationManager.getApplication() } returns application
        val messageBus = mockk<MessageBus>(relaxed = true)
        every { application.messageBus } returns messageBus
        every { project.messageBus } returns messageBus
        every { project.isDisposed } returns false
        every { messageBus.syncPublisher(SNYK_SETTINGS_TOPIC) } returns mockk(relaxed = true)
        every { messageBus.isDisposed } returns false
        every { application.getService(SnykApplicationSettingsStateService::class.java) } returns mockk(relaxed = true)
        every { application.getService(SnykAnalyticsService::class.java) } returns analyticsService
        every { project.service<SnykCliDownloaderService>().isCliDownloading() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should display right authenticate button text`() {
        val cut = SnykAuthPanel(project)
        val searchString = SnykAuthPanel.AUTHENTICATE_BUTTON_TEXT
        val jButton = UIComponentFinder.getJButtonByText(cut, searchString)
        assertNotNull(jButton)
        assertEquals("Test code now", jButton!!.text)
    }

    @Test
    fun `should display right description label`() {
        val expectedText = """
        |<html><ol>
        |  <li align="left">Authenticate to Snyk.io</li>
        |  <li align="left">Analyze code for issues and vulnerabilities</li>
        |  <li align="left">Improve you code and upgrade dependencies</li>
        |</ol>
        |</html>
        """.trimMargin()

        val cut = SnykAuthPanel(project)

        val jLabel = UIComponentFinder.getJLabelByText(cut, expectedText)
        assertNotNull(jLabel)
    }

    @Test
    fun `should send tracking to amplitude on authenticate button press`() {
        stubAuthenticationButtonServiceInteractions()

        val cut = SnykAuthPanel(project)
        val authenticateButton = UIComponentFinder.getJButtonByText(cut, SnykAuthPanel.AUTHENTICATE_BUTTON_TEXT)
        assertNotNull(authenticateButton)

        authenticateButton!!.action.actionPerformed(mockk())

        verify { analyticsService.logAuthenticateButtonIsClicked(any()) }
    }

    private fun stubAuthenticationButtonServiceInteractions() {
        justRun { project.service<AmplitudeExperimentService>().fetch(any()) }
        every { project.service<SnykCliAuthenticationService>().authenticate() } returns "testUserId"
        justRun { project.service<SnykToolWindowPanel>().cleanUiAndCaches() }
        every { analyticsService.obtainUserId(any()) } returns "testUserId"
        justRun { analyticsService.setUserId(any()) }
        justRun { analyticsService.identify() }
        justRun { analyticsService.logAuthenticateButtonIsClicked(any()) }
    }
}
