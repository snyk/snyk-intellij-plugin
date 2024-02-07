package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykCliAuthenticationService
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import org.junit.Test
import snyk.common.UIComponentFinder
import snyk.trust.WorkspaceTrustService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.awt.event.ActionEvent
import javax.swing.JButton
import javax.swing.JLabel

class SnykAuthPanelIntegTest : LightPlatform4TestCase() {

    private val analyticsService: SnykAnalyticsService = mockk(relaxed = true)
    private val cliAuthenticationService = mockk<SnykCliAuthenticationService>(relaxed = true)
    private val workspaceTrustServiceMock = mockk<WorkspaceTrustService>(relaxed = true)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        mockkStatic("snyk.trust.TrustedProjectsKt")
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true
        val application = ApplicationManager.getApplication()
        application.replaceService(SnykAnalyticsService::class.java, analyticsService, application)
        application.replaceService(WorkspaceTrustService::class.java, workspaceTrustServiceMock, application)
        project.replaceService(SnykCliAuthenticationService::class.java, cliAuthenticationService, project)
    }

    override fun tearDown() {
        unmockkAll()
        val application = ApplicationManager.getApplication()
        application.replaceService(SnykAnalyticsService::class.java, SnykAnalyticsService(), application)
        application.replaceService(WorkspaceTrustService::class.java, workspaceTrustServiceMock, application)
        project.replaceService(SnykCliAuthenticationService::class.java, SnykCliAuthenticationService(project), project)
        super.tearDown()
    }

    @Test
    fun `should display right authenticate button text`() {
        val cut = SnykAuthPanel(project)
        val authenticateButton = UIComponentFinder.getComponentByCondition(cut, JButton::class) {
            it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT
        }
        assertNotNull(authenticateButton)
        assertEquals("Trust project and scan", authenticateButton!!.text)
    }

    @Test
    fun `should display right description label`() {
        val expectedText = """
        |<html>
        |<ol>
        |  <li align="left">Authenticate to Snyk.io</li>
        |  <li align="left">Analyze code for issues and vulnerabilities</li>
        |  <li align="left">Improve your code and upgrade dependencies</li>
        |</ol>
        |<br>
        |When scanning project files, Snyk may automatically execute code<br>such as invoking the package manager to get dependency information.<br>You should only scan projects you trust. <a href="https://docs.snyk.io/ide-tools/jetbrains-plugins/folder-trust">More info</a>
        |<br>
        |<br>
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
        val event = mockk<ActionEvent>()

        val cut = SnykAuthPanel(project)
        val authenticateButton = UIComponentFinder.getComponentByCondition(cut, JButton::class) {
            it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT
        }
        every { event.source } returns authenticateButton
        every { cliAuthenticationService.authenticate() } returns ""
        assertNotNull(authenticateButton)

        authenticateButton!!.action.actionPerformed(event)

        verify { analyticsService.logAuthenticateButtonIsClicked(any()) }
    }
}
