package io.snyk.plugin.ui.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.awt.event.KeyEvent
import java.time.Duration
import org.junit.Assert.assertTrue

/**
 * E2E test for OSS (Open Source Security) scanning functionality
 * Tests the complete workflow of scanning dependencies for vulnerabilities
 */
class SnykOssScanE2ETest : E2ETestBase() {
    
    @Before
    fun setUp() {
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
    }
    
    @Test
    fun `scan project for OSS vulnerabilities`() = with(remoteRobot) {
        step("Open Java-Goof project from VCS") {
                        cloneOrOpenProject("https://github.com/JennySnyk/Java-Goof", "snyk-test-java-goof")
            
            // Wait for indexing to complete
            waitFor(duration = Duration.ofSeconds(30)) {
                try {
                    val ideFrame = find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
                    // Check that indexing is complete
                    ideFrame.findAll<CommonContainerFixture>(
                        byXpath("//div[@class='InlineProgressPanel']")
                    ).isEmpty()
                } catch (e: Exception) {
                    false
                }
            }
        }
        
        step("Open Snyk tool window") {
            openSnykToolWindow()
        }
        
        step("Enable OSS scanning in settings") {
            // Open Snyk settings
            val toolWindow = find<CommonContainerFixture>(
                byXpath("//div[@accessiblename='Snyk' or contains(@class, 'SnykToolWindow')]")
            )
            
            // Look for settings button
            val settingsButtons = toolWindow.findAll<JButtonFixture>(
                byXpath("//div[@tooltiptext='Settings' or @accessiblename='Settings']")
            )
            
            if (settingsButtons.isNotEmpty()) {
                settingsButtons.first().click()
                
                // Wait for settings to open
                waitFor(duration = Duration.ofSeconds(5)) {
                    findAll<JCheckboxFixture>(
                        byXpath("//div[@text='Snyk Open Source vulnerabilities']")
                    ).isNotEmpty()
                }
                
                // Enable OSS scanning
                val ossCheckbox = find<JCheckboxFixture>(
                    byXpath("//div[@text='Snyk Open Source vulnerabilities']")
                )
                
                if (!ossCheckbox.isSelected()) {
                    ossCheckbox.click()
                }
                
                // Close settings
                keyboard {
                    key(KeyEvent.VK_ESCAPE)
                }
            }
        }
        
        step("Trigger OSS scan") {
            val toolWindow = find<CommonContainerFixture>(
                byXpath("//div[@accessiblename='Snyk' or contains(@class, 'SnykToolWindow')]")
            )
            
            // Find and click scan button
            val scanButton = toolWindow.find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Run Snyk scan' or @text='Scan']"),
                Duration.ofSeconds(5)
            )
            scanButton.click()
            
            // Wait for scan to start
            waitFor(duration = Duration.ofSeconds(10)) {
                toolWindow.findAll<JLabelFixture>(
                    byXpath("//div[contains(@text, 'Scanning') or contains(@text, 'Finding')]")
                ).isNotEmpty()
            }
        }
        
        step("Wait for OSS results") {
            waitFor(duration = Duration.ofMinutes(2)) {
                val toolWindow = find<CommonContainerFixture>(
                    byXpath("//div[@accessiblename='Snyk' or contains(@class, 'SnykToolWindow')]")
                )
                
                // Look for OSS results section
                val ossNodes = toolWindow.findAll<ComponentFixture>(
                    byXpath("//div[contains(@text, 'Open Source Security')]")
                )
                
                ossNodes.isNotEmpty()
            }
        }
        
        step("Verify OSS vulnerability results") {
            val toolWindow = find<CommonContainerFixture>(
                byXpath("//div[@accessiblename='Snyk' or contains(@class, 'SnykToolWindow')]")
            )
            
            // Find the results tree
            val resultTree = toolWindow.find<JTreeFixture>(
                byXpath("//div[@class='Tree']")
            )
            
            // Check for vulnerability nodes
            val treeItems = resultTree.collectItems()
            
            // Look for OSS-specific items
            val ossItems = treeItems.filter { item ->
                item.nodeText.contains("gradle", ignoreCase = true) ||
                item.nodeText.contains("maven", ignoreCase = true) ||
                item.nodeText.contains("npm", ignoreCase = true) ||
                item.nodeText.contains("vulnerabilit", ignoreCase = true)
            }
            
            assertTrue("Should find OSS vulnerability results", ossItems.isNotEmpty())
            
            // Expand first vulnerability
            if (ossItems.isNotEmpty() && ossItems.first().hasChildren) {
                resultTree.expandPath(ossItems.first().path)
                
                // Wait for expansion
                waitFor {
                    resultTree.collectItems().size > treeItems.size
                }
            }
        }
        
        step("View vulnerability details") {
            val toolWindow = find<CommonContainerFixture>(
                byXpath("//div[@accessiblename='Snyk' or contains(@class, 'SnykToolWindow')]")
            )
            
            val resultTree = toolWindow.find<JTreeFixture>(
                byXpath("//div[@class='Tree']")
            )
            
            // Click on a vulnerability
            val vulnItems = resultTree.collectItems().filter {
                it.nodeText.contains("High", ignoreCase = true) ||
                it.nodeText.contains("Critical", ignoreCase = true)
            }
            
            if (vulnItems.isNotEmpty()) {
                resultTree.clickPath(vulnItems.first().path)
                
                // Wait for details panel to update
                waitFor(duration = Duration.ofSeconds(5)) {
                    toolWindow.findAll<ComponentFixture>(
                        byXpath("//div[contains(@class, 'IssueDescriptionPanel')]")
                    ).isNotEmpty()
                }
                
                // Verify details are shown
                val detailsPanels = toolWindow.findAll<ComponentFixture>(
                    byXpath("//div[contains(@class, 'IssueDescriptionPanel')]")
                )
                
                assertTrue("Vulnerability details should be displayed", detailsPanels.isNotEmpty())
            }
        }
    }
    
    @Test
    fun `filter OSS results by severity`() = with(remoteRobot) {
        step("Ensure Snyk tool window is open") {
            openSnykToolWindow()
        }
        
        step("Access filter options") {
            val toolWindow = find<CommonContainerFixture>(
                byXpath("//div[@accessiblename='Snyk' or contains(@class, 'SnykToolWindow')]")
            )
            
            // Look for filter button or dropdown
            val filterButtons = toolWindow.findAll<JButtonFixture>(
                byXpath("//div[@tooltiptext='Filter' or contains(@text, 'Filter')]")
            )
            
            if (filterButtons.isNotEmpty()) {
                filterButtons.first().click()
                
                // Wait for filter options
                waitFor(duration = Duration.ofSeconds(3)) {
                    findAll<JCheckboxFixture>(
                        byXpath("//div[contains(@text, 'Critical') or contains(@text, 'High')]")
                    ).isNotEmpty()
                }
            }
        }
        
        step("Apply severity filter") {
            // Find severity checkboxes
            val criticalCheckbox = findAll<JCheckboxFixture>(
                byXpath("//div[@text='Critical']")
            ).firstOrNull()
            
            val highCheckbox = findAll<JCheckboxFixture>(
                byXpath("//div[@text='High']")
            ).firstOrNull()
            
            // Uncheck low and medium, keep high and critical
            val lowCheckbox = findAll<JCheckboxFixture>(
                byXpath("//div[@text='Low']")
            ).firstOrNull()
            
            lowCheckbox?.let {
                if (it.isSelected()) {
                    it.click()
                }
            }
            
            // Apply filter
            keyboard {
                key(KeyEvent.VK_ENTER)
            }
        }
        
        step("Verify filtered results") {
            val toolWindow = find<CommonContainerFixture>(
                byXpath("//div[@accessiblename='Snyk' or contains(@class, 'SnykToolWindow')]")
            )
            
            // Give time for filter to apply
            Thread.sleep(2000)
            
            // Check that only high/critical issues are shown
            val resultTree = toolWindow.find<JTreeFixture>(
                byXpath("//div[@class='Tree']")
            )
            
            val visibleItems = resultTree.collectItems()
            val lowSeverityItems = visibleItems.filter {
                it.nodeText.contains("Low", ignoreCase = true)
            }
            
            assertTrue(
                "Low severity items should be filtered out",
                lowSeverityItems.isEmpty()
            )
        }
    }
    
    @After
    fun tearDown() {
        remoteRobot.cleanup()
    }
    
    // Helper methods
    private fun RemoteRobot.openSnykToolWindow() {
        val ideFrame = find<CommonContainerFixture>(
            byXpath("//div[@class='IdeFrameImpl']")
        )
        
        // Try to find and click Snyk tool window stripe button
        try {
            val snykButton = ideFrame.find<ComponentFixture>(
                byXpath("//div[@tooltiptext='Snyk' and contains(@class, 'StripeButton')]"),
                Duration.ofSeconds(5)
            )
            snykButton.click()
        } catch (e: Exception) {
            // Alternative: use View menu or Find Action
            keyboard {
                if (System.getProperty("os.name").contains("Mac")) {
                    hotKey(KeyEvent.VK_META, KeyEvent.VK_SHIFT, KeyEvent.VK_A)
                } else {
                    hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_A)
                }
            }
            
            keyboard {
                enterText("Snyk")
                key(KeyEvent.VK_ENTER)
            }
        }
        
        // Wait for tool window to appear
        waitFor(duration = Duration.ofSeconds(10)) {
            findAll<CommonContainerFixture>(
                byXpath("//div[@accessiblename='Snyk' or contains(@class, 'SnykToolWindow')]")
            ).isNotEmpty()
        }
    }
    
    private fun RemoteRobot.cleanup() {
        try {
            // Close any open dialogs
            findAll<CommonContainerFixture>(byXpath("//div[@class='MyDialog']"))
                .forEach {
                    // Close dialogs by pressing ESC
                    keyboard {
                        key(KeyEvent.VK_ESCAPE)
                    }
                }
            
            // Reset focus
            keyboard {
                key(KeyEvent.VK_ESCAPE)
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
    
    // Tree helper extension
    private fun JTreeFixture.collectItems(): List<TreeItem> {
        return callJs(
            """
            const tree = component;
            const model = tree.getModel();
            const root = model.getRoot();
            const items = [];
            
            function collectNodes(node, path) {
                const nodeInfo = {
                    nodeText: node.toString(),
                    path: path,
                    hasChildren: model.getChildCount(node) > 0
                };
                items.push(nodeInfo);
                
                for (let i = 0; i < model.getChildCount(node); i++) {
                    const child = model.getChild(node, i);
                    collectNodes(child, path.concat([i]));
                }
            }
            
            if (root) {
                collectNodes(root, []);
            }
            
            return items;
            """,
            runInEdt = true
        )
    }
    
    private fun JTreeFixture.expandPath(path: List<Int>) {
        callJs<String>(
            """
            const tree = component;
            const model = tree.getModel();
            let node = model.getRoot();
            const treePath = [node];
            
            for (const index of ${path.toString()}) {
                if (index < model.getChildCount(node)) {
                    node = model.getChild(node, index);
                    treePath.push(node);
                }
            }
            
            tree.expandPath(new TreePath(treePath));
            """,
            runInEdt = true
        )
    }
    
    private fun JTreeFixture.clickPath(path: List<Int>) {
        callJs<String>(
            """
            const tree = component;
            const model = tree.getModel();
            let node = model.getRoot();
            const treePath = [node];
            
            for (const index of ${path.toString()}) {
                if (index < model.getChildCount(node)) {
                    node = model.getChild(node, index);
                    treePath.push(node);
                }
            }
            
            const tp = new TreePath(treePath);
            tree.setSelectionPath(tp);
            tree.scrollPathToVisible(tp);
            """,
            runInEdt = true
        )
    }
    
    private data class TreeItem(
        val nodeText: String,
        val path: List<Int>,
        val hasChildren: Boolean
    )
}