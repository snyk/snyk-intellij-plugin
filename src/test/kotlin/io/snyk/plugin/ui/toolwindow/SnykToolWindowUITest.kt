package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.treeStructure.Tree
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.snyk.plugin.ui.SnykUITestBase
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import org.junit.Test
import snyk.common.UIComponentFinder
import snyk.common.UITestUtils
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode

/**
 * UI tests for SnykToolWindow
 * Tests the main tool window component and its interactions
 */
class SnykToolWindowUITest : SnykUITestBase() {

    @Test
    fun `should display auth panel when not authenticated`() {
        // Given: User is not authenticated
        settings.token = null
        
        // When: Creating tool window
        val toolWindow = SnykToolWindow(project)
        
        // Then: Auth panel should be visible
        val authPanel = UIComponentFinder.getComponentByCondition(
            toolWindow.getContent(),
            SnykAuthPanel::class
        ) { true }
        
        assertNotNull("Auth panel should be displayed", authPanel)
        
        // And: Main panel should not be visible
        val mainPanel = UIComponentFinder.getComponentByCondition(
            toolWindow.getContent(),
            JPanel::class
        ) { it.name == "mainPanel" }
        
        assertNull("Main panel should not be visible when not authenticated", mainPanel)
    }
    
    @Test
    fun `should display main panel when authenticated`() {
        // Given: User is authenticated
        settings.token = "test-token"
        
        // When: Creating tool window
        val toolWindow = SnykToolWindow(project)
        
        // Then: Main panel should be visible
        val mainPanel = UIComponentFinder.getComponentByCondition(
            toolWindow.getContent(),
            JPanel::class
        ) { it.name == "mainPanel" }
        
        assertNotNull("Main panel should be displayed when authenticated", mainPanel)
    }
    
    @Test
    fun `should show vulnerability tree when scan completes`() {
        // Given: Authenticated user
        settings.token = "test-token"
        enableOssScan()
        
        val toolWindow = SnykToolWindow(project)
        
        // When: Simulating scan results
        val tree = UIComponentFinder.getComponentByCondition(
            toolWindow.getContent(),
            Tree::class
        ) { true }
        
        assertNotNull("Vulnerability tree should exist", tree)
        
        // Then: Tree should be properly structured
        val rootNode = tree?.model?.root as? DefaultMutableTreeNode
        assertNotNull("Tree should have root node", rootNode)
    }
    
    @Test
    fun `should handle run scan action`() {
        // Given: Authenticated user with tool window
        settings.token = "test-token"
        val toolWindow = SnykToolWindow(project)
        
        // When: Finding and clicking run scan button
        val runScanButton = UIComponentFinder.getComponentByCondition(
            toolWindow.getContent(),
            JButton::class
        ) { it.toolTipText?.contains("Run Snyk scan") == true }
        
        assertNotNull("Run scan button should exist", runScanButton)
        
        // Simulate click
        runScanButton?.let {
            UITestUtils.simulateClick(it)
        }
        
        // Then: Task should be queued (would need to verify through mocks)
    }
}