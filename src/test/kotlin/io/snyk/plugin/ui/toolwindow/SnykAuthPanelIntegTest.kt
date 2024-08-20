package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.services.SnykCliAuthenticationService
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import org.junit.Test
import snyk.common.UIComponentFinder
import snyk.trust.WorkspaceTrustService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import javax.swing.JButton
import javax.swing.JLabel

class SnykAuthPanelIntegTest : LightPlatform4TestCase() {

    private val cliAuthenticationService = mockk<SnykCliAuthenticationService>(relaxed = true)
    private val workspaceTrustServiceMock = mockk<WorkspaceTrustService>(relaxed = true)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        mockkStatic("snyk.trust.TrustedProjectsKt")
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true
        val application = ApplicationManager.getApplication()
        application.replaceService(WorkspaceTrustService::class.java, workspaceTrustServiceMock, application)
        project.replaceService(SnykCliAuthenticationService::class.java, cliAuthenticationService, project)
    }

    override fun tearDown() {
        unmockkAll()
        val application = ApplicationManager.getApplication()
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
}
