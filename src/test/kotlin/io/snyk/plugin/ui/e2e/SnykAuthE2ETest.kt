package io.snyk.plugin.ui.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException
import com.intellij.remoterobot.utils.waitFor
import com.intellij.remoterobot.utils.keyboard
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration

/**
 * True E2E UI test using Remote-Robot framework
 * This test launches a real IDE instance and interacts with it via UI automation
 */
class SnykAuthE2ETest {
    private lateinit var remoteRobot: RemoteRobot
    
    @Before
    fun setUp() {
        // Connect to the running IDE with robot-server plugin
        // Run ./gradlew runIdeForUiTests before running this test
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
    }
    
    @Test
    fun `should display Snyk tool window and authenticate`() = with(remoteRobot) {
        step("Wait for IDE to start") {
            waitFor(duration = Duration.ofSeconds(30)) {
                try {
                    find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
        
        step("Open Snyk tool window") {
            // Click on Snyk tool window button
            val ideFrame = find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
            
            // Try to find Snyk tool window stripe button
            val snykToolWindowButton = ideFrame.find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Snyk' and @class='StripeButton']"),
                Duration.ofSeconds(10)
            )
            snykToolWindowButton.click()
        }
        
        step("Verify authentication panel is shown") {
            // Wait for Snyk tool window to open
            waitFor(duration = Duration.ofSeconds(10)) {
                try {
                    findAll<CommonContainerFixture>(
                        byXpath("//div[@class='SnykAuthPanel']")
                    ).isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
            
            // Verify trust and scan button exists
            val authPanel = find<CommonContainerFixture>(
                byXpath("//div[@class='SnykAuthPanel']")
            )
            
            val trustButton = authPanel.find<JButtonFixture>(
                byXpath("//div[@text='Trust project and scan']")
            )
            
            assertTrue(trustButton.isEnabled())
        }
    }
    
    @After
    fun tearDown() {
        // Close any open dialogs
        try {
            remoteRobot.findAll<CommonContainerFixture>(
                byXpath("//div[@class='MyDialog']")
            ).forEach { 
                // Close dialogs by clicking cancel or ESC
                try {
                    it.button("Cancel").click()
                } catch (e: Exception) {
                    // If no cancel button, press ESC
                    remoteRobot.keyboard {
                        key(java.awt.event.KeyEvent.VK_ESCAPE)
                    }
                }
            }
        } catch (e: WaitForConditionTimeoutException) {
            // No dialogs to close
        }
    }
}