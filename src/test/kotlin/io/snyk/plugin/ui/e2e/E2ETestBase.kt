package io.snyk.plugin.ui.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
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
                
                // Find URL input field and enter repository URL
                val urlFields = dialog.findAll<CommonContainerFixture>(byXpath("//div[@class='JTextField']"))
                if (urlFields.isNotEmpty()) {
                    // First field is usually the URL field
                    urlFields[0].click()
                    keyboard {
                        enterText(repoUrl)
                    }
                }
                
                // Find directory field and set a temp directory
                if (urlFields.size > 1) {
                    urlFields[1].click()
                    keyboard {
                        hotKey(KeyEvent.VK_META, KeyEvent.VK_A) // Select all
                        enterText(System.getProperty("java.io.tmpdir") + projectName)
                    }
                }
                
                // Click Clone button in dialog
                val cloneDialogButton = dialog.find<JButtonFixture>(byXpath("//div[@text='Clone']"))
                cloneDialogButton.click()
                
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