package io.snyk.plugin.ui.toolwindow

import com.intellij.ui.treeStructure.Tree
import io.snyk.plugin.ui.SnykUITestBase
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import org.junit.Test
import snyk.common.UIComponentFinder
import snyk.common.UITestUtils
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode

/**
 * UI tests for SnykToolWindow components
 * Tests the tool window panel and its interactions
 */
class SnykToolWindowUITest : SnykUITestBase() {

    @Test  
    fun `should create auth panel when not authenticated`() {
        // Given: User is not authenticated
        settings.token = null
        
        // When: Creating auth panel directly
        val authPanel = SnykAuthPanel(project)
        
        // Then: Panel should be created successfully
        assertNotNull("Auth panel should be created", authPanel)
        
        // And: Should have authenticate button
        val authenticateButton = UIComponentFinder.getComponentByCondition(
            authPanel,
            JButton::class
        ) { it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT }
        
        assertNotNull("Authenticate button should exist", authenticateButton)
    }
    
    @Test
    fun `should enable authenticate button in auth panel`() {
        // Given: User is not authenticated
        settings.token = null
        
        // When: Creating auth panel
        val authPanel = SnykAuthPanel(project)
        
        // Then: Button should be enabled
        val button = UIComponentFinder.getComponentByCondition(
            authPanel,
            JButton::class
        ) { it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT }
        
        assertTrue("Authenticate button should be enabled", button?.isEnabled == true)
    }
    
    @Test
    fun `should display correct label text in auth panel`() {
        // Given: User is not authenticated  
        settings.token = null
        
        // When: Creating auth panel
        val authPanel = SnykAuthPanel(project)
        
        // Then: Should have correct description label with authentication instructions
        val label = UIComponentFinder.getComponentByCondition(
            authPanel,
            JLabel::class
        ) { it.text?.contains("Authenticate to Snyk.io") == true }
        
        assertNotNull("Description label should exist", label)
        assertTrue("Label should contain authentication instructions", label?.text?.contains("Analyze code for issues") == true)
    }
    
    @Test
    fun `should simulate button click in auth panel`() {
        // Given: Auth panel with button
        settings.token = null
        val authPanel = SnykAuthPanel(project)
        
        // When: Finding and clicking button
        val button = UIComponentFinder.getComponentByCondition(
            authPanel,
            JButton::class
        ) { it.text == SnykAuthPanel.TRUST_AND_SCAN_BUTTON_TEXT }
        
        assertNotNull("Button should exist", button)
        
        // Then: Can simulate click without errors
        button?.let {
            UITestUtils.simulateClick(it)
            // In real test, would verify action through mocks
        }
    }
}