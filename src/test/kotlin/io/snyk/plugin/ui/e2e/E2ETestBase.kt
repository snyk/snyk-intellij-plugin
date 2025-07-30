package io.snyk.plugin.ui.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import com.intellij.remoterobot.stepsProcessing.step
import java.awt.event.KeyEvent
import java.time.Duration

/**
 * Base class for E2E tests providing common functionality
 */
abstract class E2ETestBase {
    
    protected lateinit var remoteRobot: RemoteRobot
    
    /**
     * Clones a repository from VCS or assumes project is already open
     * @param repoUrl The GitHub repository URL to clone
     * @param projectName The name for the cloned project directory
     */
    protected fun cloneOrOpenProject(repoUrl: String, projectName: String) {
        with(remoteRobot) {
            try {
                // Focus the IDE application window
                runJs("""
                    importPackage(com.intellij.openapi.wm)
                    importPackage(java.lang)
                    var wm = WindowManager.getInstance()
                    var window = wm.getFrame(null)
                    if (window != null) {
                        window.toFront()
                        window.requestFocus()
                        window.setAlwaysOnTop(true)
                        Thread.sleep(100)
                        window.setAlwaysOnTop(false)
                    }
                """)
                Thread.sleep(1000) // Give time for window to come to front
                
                // Try to find the welcome screen
                val welcomeFrame = find<CommonContainerFixture>(
                    byXpath("//div[@class='FlatWelcomeFrame']"),
                    Duration.ofSeconds(2)
                )
                
                // Find and click "Clone Repository" button
                val cloneButtons = welcomeFrame.findAll<JButtonFixture>(
                    byXpath("//div[@accessiblename='Clone Repository']")
                )
                cloneButtons.first().click()
                
                // Wait for VCS dialog
                Thread.sleep(2000)
                
                // Find the dialog
                val dialog = find<CommonContainerFixture>(byXpath("//div[@class='MyDialog']"))
                
                // Find URL input field - try multiple approaches
                var fieldsFilledSuccessfully = false
                
                // Approach 1: Try TextFieldWithBrowseButton
                try {
                    val browseFields = dialog.findAll<CommonContainerFixture>(byXpath("//div[@class='TextFieldWithBrowseButton']"))
                    println("Approach 1: Found ${browseFields.size} TextFieldWithBrowseButton fields")
                    
                    if (browseFields.size >= 2) {
                        println("Using TextFieldWithBrowseButton approach")
                        
                        // Click inside the first field (URL)
                        browseFields[0].click()
                        Thread.sleep(500)
                        keyboard {
                            val isMac = System.getProperty("os.name").contains("Mac")
                            val selectAllKey = if (isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL
                            hotKey(selectAllKey, KeyEvent.VK_A)
                            enterText(repoUrl)
                        }
                        println("Entered URL: $repoUrl")
                        
                        // Click inside the second field (Directory)
                        Thread.sleep(500)
                        browseFields[1].click()
                        Thread.sleep(500)
                        keyboard {
                            val isMac = System.getProperty("os.name").contains("Mac")
                            val selectAllKey = if (isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL
                            hotKey(selectAllKey, KeyEvent.VK_A)
                            enterText(System.getProperty("java.io.tmpdir") + projectName)
                        }
                        println("Entered directory: ${System.getProperty("java.io.tmpdir") + projectName}")
                        fieldsFilledSuccessfully = true
                    }
                } catch (e: Exception) {
                    println("Approach 1 failed: ${e.message}")
                }
                
                // Approach 2: Try finding by more specific xpath
                if (!fieldsFilledSuccessfully) {
                    try {
                        println("Approach 2: Looking for text fields within TextFieldWithBrowseButton")
                        
                        // Look for text fields that are children of TextFieldWithBrowseButton
                        val textFieldsInBrowse = dialog.findAll<JTextFieldFixture>(
                            byXpath("//div[@class='TextFieldWithBrowseButton']//div[@class='JTextField']")
                        )
                        println("Found ${textFieldsInBrowse.size} text fields inside browse buttons")
                        
                        if (textFieldsInBrowse.size >= 2) {
                            textFieldsInBrowse[0].text = repoUrl
                            println("Set URL using nested text field")
                            
                            textFieldsInBrowse[1].text = System.getProperty("java.io.tmpdir") + projectName
                            println("Set directory using nested text field")
                            
                            fieldsFilledSuccessfully = true
                        }
                    } catch (e: Exception) {
                        println("Approach 2 failed: ${e.message}")
                    }
                }
                
                // Approach 3: Look for any input-like components
                if (!fieldsFilledSuccessfully) {
                    try {
                        println("Approach 3: Looking for any JTextField in dialog")
                        
                        val anyTextFields = dialog.findAll<JTextFieldFixture>(byXpath(".//div[@class='JTextField']"))
                        println("Found ${anyTextFields.size} JTextField elements anywhere in dialog")
                        
                        if (anyTextFields.size >= 2) {
                            anyTextFields[0].text = repoUrl
                            println("Set URL using any text field approach")
                            
                            anyTextFields[1].text = System.getProperty("java.io.tmpdir") + projectName
                            println("Set directory using any text field approach")
                            
                            fieldsFilledSuccessfully = true
                        }
                    } catch (e: Exception) {
                        println("Approach 3 failed: ${e.message}")
                    }
                }
                
                // Approach 4: Try clicking in the general area and using keyboard
                if (!fieldsFilledSuccessfully) {
                    try {
                        println("Approach 4: Using keyboard navigation")
                        
                        // The URL field should already be focused when dialog opens
                        Thread.sleep(500)
                        
                        // Enter URL in the already-focused field
                        keyboard {
                            val isMac = System.getProperty("os.name").contains("Mac")
                            val selectAllKey = if (isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL
                            
                            // Clear and enter URL
                            hotKey(selectAllKey, KeyEvent.VK_A)
                            enterText(repoUrl)
                            println("Entered URL in already-focused field")
                            
                            // Tab to directory field
                            Thread.sleep(300)
                            key(KeyEvent.VK_TAB)
                            Thread.sleep(300)
                            
                            // Clear and enter directory
                            hotKey(selectAllKey, KeyEvent.VK_A)
                            enterText(System.getProperty("java.io.tmpdir") + projectName)
                            println("Entered directory via keyboard navigation")
                        }
                        
                        fieldsFilledSuccessfully = true
                    } catch (e: Exception) {
                        println("Approach 4 failed: ${e.message}")
                    }
                }
                
                // Only click Clone button if we successfully filled the fields
                if (fieldsFilledSuccessfully) {
                    Thread.sleep(1000) // Wait before clicking clone
                    val cloneDialogButton = dialog.find<JButtonFixture>(byXpath("//div[@text='Clone']"))
                    println("Clicking Clone button")
                    cloneDialogButton.click()
                    println("Clone button clicked, waiting for project to load...")
                } else {
                    println("ERROR: Could not fill URL and directory fields, skipping clone button click")
                }
                
            } catch (e: Exception) {
                // We might already have a project open
                println("Welcome screen not found or VCS clone failed, assuming project is already open: ${e.message}")
            }
            
            // Wait for project to be loaded
            waitFor(Duration.ofMinutes(2)) {
                // Check if we can find the main IDE window
                findAll<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']")).isNotEmpty()
            }
        }
    }
    
    /**
     * Opens the Snyk tool window
     */
    protected fun openSnykToolWindow() {
        with(remoteRobot) {
            // Click on Snyk tool window button
            val ideFrame = find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
            
            // Try to find Snyk tool window stripe button
            val snykToolWindowButton = ideFrame.find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Snyk' and @class='StripeButton']"),
                Duration.ofSeconds(10)
            )
            snykToolWindowButton.click()
            
            // Wait for tool window to be visible
            waitFor(Duration.ofSeconds(10)) {
                findAll<CommonContainerFixture>(byXpath("//div[@class='SnykAuthPanel']")).isNotEmpty() ||
                findAll<CommonContainerFixture>(byXpath("//div[@class='SnykToolWindow']")).isNotEmpty()
            }
        }
    }
}