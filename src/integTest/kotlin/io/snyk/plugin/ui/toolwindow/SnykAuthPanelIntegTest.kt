package io.snyk.plugin.ui.toolwindow

import snyk.common.UIComponentFinder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykCliAuthenticationService
import org.junit.Test
import javax.swing.JButton
import javax.swing.JLabel

class SnykAuthPanelIntegTest : LightPlatform4TestCase() {

    private val analyticsService: SnykAnalyticsService = mockk(relaxed = true)
    private val cliAuthenticationService = mockk<SnykCliAuthenticationService>(relaxed = true)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        val application = ApplicationManager.getApplication()
        application.replaceService(SnykAnalyticsService::class.java, analyticsService, application)
        project.replaceService(SnykCliAuthenticationService::class.java, cliAuthenticationService, project)
    }

    override fun tearDown() {
        unmockkAll()
        val application = ApplicationManager.getApplication()
        application.replaceService(SnykAnalyticsService::class.java, SnykAnalyticsService(), application)
        project.replaceService(SnykCliAuthenticationService::class.java, SnykCliAuthenticationService(project), project)
        super.tearDown()
    }

    @Test
    fun `should display right authenticate button text`() {
        val cut = SnykAuthPanel(project)
        val authenticateButton = UIComponentFinder.getComponentByCondition(cut, JButton::class) {
            it.text == SnykAuthPanel.AUTHENTICATE_BUTTON_TEXT
        }
        assertNotNull(authenticateButton)
        assertEquals("Test code now", authenticateButton!!.text)
    }

    @Test
    fun `should display right description label`() {
        val expectedText = """
        |<html><ol>
        |  <li align="left">Authenticate to Snyk.io</li>
        |  <li align="left">Analyze code for issues and vulnerabilities</li>
        |  <li align="left">Improve your code and upgrade dependencies</li>
        |</ol>
        |</html>
        """.trimMargin()

        val cut = SnykAuthPanel(project)

        val jLabel = UIComponentFinder.getComponentByCondition(cut, JLabel::class) {
            it.text == expectedText
        }
        assertNotNull(jLabel)
    }

    @Test
    fun `should send tracking to amplitude on authenticate button press`() {

        every { cliAuthenticationService.authenticate() } returns ""

        val cut = SnykAuthPanel(project)
        val authenticateButton = UIComponentFinder.getComponentByCondition(cut, JButton::class) {
            it.text == SnykAuthPanel.AUTHENTICATE_BUTTON_TEXT
        }
        assertNotNull(authenticateButton)

        authenticateButton!!.action.actionPerformed(mockk())

        verify { analyticsService.logAuthenticateButtonIsClicked(any()) }
    }
}
