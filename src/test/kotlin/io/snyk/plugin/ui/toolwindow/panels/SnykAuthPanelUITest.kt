package io.snyk.plugin.ui.toolwindow.panels

import io.snyk.plugin.ui.SnykUITestBase
import org.junit.Test
import snyk.common.UIComponentFinder
import snyk.common.UITestUtils
import javax.swing.JButton
import javax.swing.JLabel

/**
 * UI tests for SnykAuthPanel
 * Example test to validate the UI testing infrastructure
 */
class SnykAuthPanelUITest : SnykUITestBase() {

    @Test
    fun `should display authentication panel when not authenticated`() {
        // Given: User is not authenticated
        settings.token = null

        // When: Creating auth panel
        val authPanel = SnykAuthPanel(project)

        // Then: Should display proper UI elements
        val authenticateButton = UIComponentFinder.getComponentByCondition(authPanel, JButton::class) {
            it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT
        }
        assertNotNull("Authenticate button should be present", authenticateButton)
        assertEquals("Trust project and scan", authenticateButton!!.text)

        // Check for description label
        val descriptionLabel = UIComponentFinder.getComponentByCondition(authPanel, JLabel::class) {
            it.text?.contains("Authenticate to Snyk.io") ?: false
        }
        assertNotNull("Description label should be present", descriptionLabel)
    }

    @Test
    fun `should enable authenticate button when panel is shown`() {
        // Given: User is not authenticated
        settings.token = null

        // When: Creating auth panel
        val authPanel = SnykAuthPanel(project)
        waitForUiUpdates()

        // Then: Button should be enabled
        val authenticateButton = UIComponentFinder.getComponentByCondition(authPanel, JButton::class) {
            it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT
        }
        assertTrue("Authenticate button should be enabled", authenticateButton!!.isEnabled)
    }

    @Test
    fun `should have proper button action listener`() {
        // Given: User is not authenticated
        settings.token = null

        // When: Creating auth panel
        val authPanel = SnykAuthPanel(project)

        // Then: Button should have action listener
        val authenticateButton = UIComponentFinder.getComponentByCondition(authPanel, JButton::class) {
            it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT
        }
        assertNotNull("Button should have action listeners", authenticateButton!!.actionListeners)
        assertTrue("Button should have at least one action listener", authenticateButton.actionListeners.isNotEmpty())
    }
}