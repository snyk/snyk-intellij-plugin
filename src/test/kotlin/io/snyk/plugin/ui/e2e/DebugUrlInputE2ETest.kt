package io.snyk.plugin.ui.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.Test
import java.awt.event.KeyEvent
import java.time.Duration

class DebugUrlInputE2ETest {
    
    @Test
    fun `debug URL input in clone dialog`() {
        val remoteRobot = RemoteRobot("http://127.0.0.1:8082")
        
        with(remoteRobot) {
            // Wait for IDE
            waitFor(Duration.ofSeconds(30)) {
                try {
                    find<CommonContainerFixture>(byXpath("//div[@class='FlatWelcomeFrame' or @class='IdeFrameImpl']"))
                    true
                } catch (e: Exception) {
                    false
                }
            }
            
            try {
                // Focus window
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
                Thread.sleep(1000)
                
                // Find welcome frame
                val welcomeFrame = find<CommonContainerFixture>(byXpath("//div[@class='FlatWelcomeFrame']"))
                
                // Click Clone Repository
                val cloneButtons = welcomeFrame.findAll<JButtonFixture>(
                    byXpath("//div[@accessiblename='Clone Repository']")
                )
                println("Found ${cloneButtons.size} clone buttons")
                cloneButtons.first().click()
                
                Thread.sleep(2000)
                
                // Find dialog
                val dialog = find<CommonContainerFixture>(byXpath("//div[@class='MyDialog']"))
                println("Found clone dialog")
                
                // Try different ways to find text fields
                val textFields1 = dialog.findAll<CommonContainerFixture>(byXpath("//div[@class='JTextField']"))
                println("Found ${textFields1.size} text fields using CommonContainerFixture")
                
                val textFields2 = dialog.findAll<JTextFieldFixture>(byXpath("//div[@class='JTextField']"))
                println("Found ${textFields2.size} text fields using JTextFieldFixture")
                
                // Try to find by component type
                val textFields3 = dialog.findAll<JTextFieldFixture>(byXpath("//div[@javaclass='javax.swing.JTextField']"))
                println("Found ${textFields3.size} text fields using javaclass")
                
                // Try the URL field specifically
                if (textFields2.isNotEmpty()) {
                    println("Trying to interact with first JTextFieldFixture")
                    val urlField = textFields2[0]
                    urlField.click()
                    Thread.sleep(500)
                    
                    // Try different input methods
                    println("Method 1: Using text property")
                    try {
                        urlField.text = "https://github.com/snyk-labs/nodejs-goof"
                        println("Set text property successfully")
                    } catch (e: Exception) {
                        println("Failed to set text property: ${e.message}")
                    }
                    
                    Thread.sleep(1000)
                    
                    println("Method 2: Using keyboard")
                    urlField.click()
                    keyboard {
                        val isMac = System.getProperty("os.name").contains("Mac")
                        val selectAllKey = if (isMac) KeyEvent.VK_META else KeyEvent.VK_CONTROL
                        
                        hotKey(selectAllKey, KeyEvent.VK_A)
                        key(KeyEvent.VK_DELETE)
                        enterText("https://github.com/test/test")
                    }
                    println("Entered text using keyboard")
                    
                    Thread.sleep(2000)
                    
                    // Check the value
                    println("Current text field value: ${urlField.text}")
                }
                
            } catch (e: Exception) {
                println("Error during test: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}