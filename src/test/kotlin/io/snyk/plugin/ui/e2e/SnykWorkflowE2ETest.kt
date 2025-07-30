package io.snyk.plugin.ui.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.*
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.awt.event.KeyEvent
import java.time.Duration

/**
 * Comprehensive E2E test demonstrating various UI testing capabilities
 * This test covers a complete workflow: authentication, scanning, and result viewing
 */
class SnykWorkflowE2ETest {
    private lateinit var remoteRobot: RemoteRobot
    
    @Before
    fun setUp() {
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
    }
    
    @Test
    fun `complete Snyk workflow - authenticate, scan and view results`() = with(remoteRobot) {
        step("Wait for IDE to fully load") {
            waitFor(duration = Duration.ofSeconds(60)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@class='IdeFrameImpl']")
                ).isNotEmpty()
            }
        }
        
        step("Open project or create new one") {
            val ideFrame = find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
            
            // Check if welcome screen is shown
            val welcomeScreens = findAll<CommonContainerFixture>(
                byXpath("//div[@class='FlatWelcomeFrame']")
            )
            
            if (welcomeScreens.isNotEmpty()) {
                // Click "Open" button on welcome screen
                val openButton = find<JButtonFixture>(
                    byXpath("//div[@text='Open']")
                )
                openButton.click()
                
                // Handle file chooser dialog
                waitFor {
                    findAll<CommonContainerFixture>(
                        byXpath("//div[@title='Open File or Project']")
                    ).isNotEmpty()
                }
                
                // Cancel dialog for now (in real test, would select a project)
                keyboard {
                    key(KeyEvent.VK_ESCAPE)
                }
            }
        }
        
        step("Open Snyk tool window") {
            val ideFrame = find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
            
            // Try multiple ways to open Snyk tool window
            try {
                // Method 1: Click on tool window stripe button
                val snykStripeButton = ideFrame.find<ComponentFixture>(
                    byXpath("//div[@tooltiptext='Snyk' and contains(@class, 'StripeButton')]"),
                    Duration.ofSeconds(5)
                )
                snykStripeButton.click()
            } catch (e: Exception) {
                // Method 2: Use View menu
                step("Open via View menu") {
                    // Open View menu
                    if (System.getProperty("os.name").contains("Mac")) {
                        keyboard {
                            hotKey(KeyEvent.VK_META, KeyEvent.VK_SHIFT, KeyEvent.VK_A)
                        }
                    } else {
                        keyboard {
                            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_A)
                        }
                    }
                    
                    // Type to search for Snyk
                    keyboard {
                        enterText("Snyk")
                    }
                    
                    // Select first result
                    keyboard {
                        key(KeyEvent.VK_ENTER)
                    }
                }
            }
        }
        
        step("Verify Snyk tool window is displayed") {
            waitFor(duration = Duration.ofSeconds(10)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[contains(@class, 'SnykToolWindow')]")
                ).isNotEmpty() ||
                findAll<CommonContainerFixture>(
                    byXpath("//div[@accessiblename='Snyk']")
                ).isNotEmpty()
            }
        }
        
        step("Check authentication status") {
            // Look for auth panel or scan results
            val authPanels = findAll<CommonContainerFixture>(
                byXpath("//div[contains(@class, 'SnykAuthPanel')]")
            )
            
            if (authPanels.isNotEmpty()) {
                step("Authenticate with Snyk") {
                    // Find and click authenticate button
                    val authButton = find<JButtonFixture>(
                        byXpath("//div[@text='Trust project and scan' or @text='Connect to Snyk']")
                    )
                    
                    assertTrue(authButton.isEnabled())
                    authButton.click()
                    
                    // Wait for authentication dialog or browser to open
                    waitFor(duration = Duration.ofSeconds(30)) {
                        // Check if authentication completed
                        findAll<CommonContainerFixture>(
                            byXpath("//div[contains(@class, 'SnykAuthPanel')]")
                        ).isEmpty()
                    }
                }
            }
        }
        
        step("Trigger Snyk scan") {
            // Find scan button
            val scanButtons = findAll<JButtonFixture>(
                byXpath("//div[@tooltiptext='Run Snyk scan' or @text='Scan']")
            )
            
            if (scanButtons.isNotEmpty()) {
                scanButtons.first().click()
                
                // Wait for scan to start
                waitFor(duration = Duration.ofSeconds(10)) {
                    findAll<CommonContainerFixture>(
                        byXpath("//div[contains(@text, 'Scanning') or contains(@text, 'Analyzing')]")
                    ).isNotEmpty()
                }
            }
        }
        
        step("Wait for scan results") {
            waitFor(duration = Duration.ofMinutes(2)) {
                // Look for result tree or no issues message
                val resultTrees = findAll<JTreeFixture>(
                    byXpath("//div[@class='Tree']")
                )
                val noIssuesMessages = findAll<JLabelFixture>(
                    byXpath("//div[contains(@text, 'No issues found')]")
                )
                
                resultTrees.isNotEmpty() || noIssuesMessages.isNotEmpty()
            }
        }
        
        step("Verify results are displayed") {
            // Check for issue tree
            val issueTrees = findAll<JTreeFixture>(
                byXpath("//div[@class='Tree']")
            )
            
            if (issueTrees.isNotEmpty()) {
                val tree = issueTrees.first()
                
                // Get root node
                val rootItem = tree.collectItems().firstOrNull()
                assertTrue(rootItem != null, "Issue tree should have items")
                
                // Expand first node if possible
                if (rootItem != null && rootItem.hasChildren) {
                    tree.expandPath(rootItem.path)
                    
                    // Verify children are visible
                    waitFor {
                        tree.collectItems().size > 1
                    }
                }
            }
        }
    }
    
    @Test
    fun `navigate through Snyk settings`() = with(remoteRobot) {
        step("Open IDE settings") {
            waitFor(duration = Duration.ofSeconds(30)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@class='IdeFrameImpl']")
                ).isNotEmpty()
            }
            
            // Open settings using keyboard shortcut
            keyboard {
                if (System.getProperty("os.name").contains("Mac")) {
                    hotKey(KeyEvent.VK_META, KeyEvent.VK_COMMA)
                } else {
                    hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_S)
                }
            }
        }
        
        step("Navigate to Snyk settings") {
            waitFor(duration = Duration.ofSeconds(10)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@title='Settings' or @title='Preferences']")
                ).isNotEmpty()
            }
            
            val settingsDialog = find<CommonContainerFixture>(
                byXpath("//div[@title='Settings' or @title='Preferences']")
            )
            
            // Search for Snyk
            val searchField = settingsDialog.find<JTextFieldFixture>(
                byXpath("//div[@class='SearchTextField']"),
                Duration.ofSeconds(5)
            )
            searchField.setText("Snyk")
            
            // Click on Snyk in the tree
            val settingsTree = settingsDialog.find<JTreeFixture>(
                byXpath("//div[@class='Tree']")
            )
            
            val snykNode = settingsTree.collectItems().find { 
                it.nodeText.contains("Snyk", ignoreCase = true) 
            }
            
            if (snykNode != null) {
                settingsTree.clickPath(snykNode.path)
            }
        }
        
        step("Verify Snyk settings panel") {
            val settingsDialog = find<CommonContainerFixture>(
                byXpath("//div[@title='Settings' or @title='Preferences']")
            )
            
            // Check for Snyk-specific settings
            val tokenFields = settingsDialog.findAll<JTextFieldFixture>(
                byXpath("//div[@accessiblename='Token' or @tooltiptext='Snyk API Token']")
            )
            
            assertTrue(tokenFields.isNotEmpty(), "Token field should be present")
            
            // Check for scan type checkboxes
            val checkboxes = settingsDialog.findAll<JCheckboxFixture>(
                byXpath("//div[@class='JCheckBox']")
            )
            
            assertTrue(checkboxes.isNotEmpty(), "Scan type checkboxes should be present")
        }
        
        step("Close settings dialog") {
            keyboard {
                key(KeyEvent.VK_ESCAPE)
            }
        }
    }
    
    @After
    fun tearDown() {
        // Close any open dialogs
        try {
            remoteRobot.findAll<CommonContainerFixture>(byXpath("//div[@class='MyDialog']"))
                .forEach {
                    // Close dialogs by pressing ESC
                    remoteRobot.keyboard {
                        key(KeyEvent.VK_ESCAPE)
                    }
                }
        } catch (e: Exception) {
            // Ignore if no dialogs
        }
        
        // Close any open tool windows
        try {
            remoteRobot.keyboard {
                hotKey(KeyEvent.VK_SHIFT, KeyEvent.VK_ESCAPE)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    // Helper extension functions
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
            
            collectNodes(root, []);
            return items;
            """,
            runInEdt = true
        )
    }
    
    private fun JTreeFixture.expandPath(path: List<Int>) {
        callJs(
            """
            const tree = component;
            const model = tree.getModel();
            let node = model.getRoot();
            const treePath = [node];
            
            for (const index of ${path.toString()}) {
                node = model.getChild(node, index);
                treePath.push(node);
            }
            
            tree.expandPath(new TreePath(treePath));
            """,
            runInEdt = true
        )
    }
    
    private fun JTreeFixture.clickPath(path: List<Int>) {
        callJs(
            """
            const tree = component;
            const model = tree.getModel();
            let node = model.getRoot();
            const treePath = [node];
            
            for (const index of ${path.toString()}) {
                node = model.getChild(node, index);
                treePath.push(node);
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