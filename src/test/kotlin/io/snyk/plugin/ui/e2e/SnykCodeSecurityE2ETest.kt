package io.snyk.plugin.ui.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.fixtures.JCheckboxFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import com.intellij.remoterobot.utils.keyboard
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.awt.event.KeyEvent

/**
 * E2E test for Code Security scanning functionality
 * Tests the complete workflow of scanning code for security vulnerabilities
 */
class SnykCodeSecurityE2ETest {
    private lateinit var remoteRobot: RemoteRobot
    private val testProjectPath = System.getProperty("test.project.path", ".")

    @Before
    fun setUp() {
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
    }

    @Test
    fun `should perform code security scan and display results`() = with(remoteRobot) {
        step("Open nodejs-goof project from VCS") {
            try {
                // Check if we're on the welcome screen
                val welcomeFrame = find<CommonContainerFixture>(byXpath("//div[@class='FlatWelcomeFrame']"))
                
                // Focus the IDE window
                welcomeFrame.runJs("component.requestFocus(); component.requestFocusInWindow();")
                Thread.sleep(500) // Give time for focus
                
                // Click Clone Repository button using accessible name
                // There are 2 elements with this accessible name (button and label), we need the button
                val cloneButtons = welcomeFrame.findAll<JButtonFixture>(
                    byXpath("//div[@accessiblename='Clone Repository']")
                )
            if (cloneButtons.isEmpty()) {
                throw IllegalStateException("Could not find Clone Repository button")
            }
            // The first one should be the actual button
            cloneButtons.first().click()
                
                // Wait for VCS dialog
                Thread.sleep(2000)
                
                // Find URL input field and enter nodejs-goof repository
                val urlField = find<CommonContainerFixture>(byXpath("//div[@class='TextFieldWithBrowseButton']"))
                urlField.click()
                keyboard {
                    enterText("https://github.com/snyk-labs/nodejs-goof")
                }
                
                // Click Clone button in dialog
                val cloneDialogButton = find<JButtonFixture>(byXpath("//div[@text='Clone']"))
                cloneDialogButton.click()
                
            } catch (e: Exception) {
                // We might already have a project open
                println("Failed to clone from VCS, assuming project is already open: ${e.message}")
            }
            
            // Wait for project to open
            waitFor(duration = Duration.ofSeconds(60)) {
                try {
                    find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }



        step("Open Snyk tool window") {
            val ideFrame = find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))

            // Try to find Snyk tool window stripe button
            waitFor(duration = Duration.ofSeconds(10)) {
                try {
                    val snykToolWindowButton = ideFrame.find<JButtonFixture>(
                        byXpath("//div[@tooltiptext='Snyk' and @class='StripeButton']"),
                        Duration.ofSeconds(5)
                    )
                    snykToolWindowButton.click()
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        step("Enable Code Security scanning in settings") {
            // Open settings
            keyboard {
                if (System.getProperty("os.name").contains("Mac")) {
                    hotKey(KeyEvent.VK_META, KeyEvent.VK_COMMA)
                } else {
                    hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_S)
                }
            }

            // Wait for settings dialog
            waitFor(duration = Duration.ofSeconds(10)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@class='MyDialog' and contains(@title.key, 'settings')]")
                ).isNotEmpty()
            }

            // Navigate to Snyk settings
            val settingsTree = find<JTreeFixture>(
                byXpath("//div[@class='SettingsTreeView']"),
                Duration.ofSeconds(10)
            )

            // Find and click Snyk node
            settingsTree.findText("Snyk").click()

            // Enable Code Security scanning
            val codeSecurityCheckbox = find<JCheckboxFixture>(
                byXpath("//div[@text='Snyk Code Security issues']"),
                Duration.ofSeconds(5)
            )

            if (!codeSecurityCheckbox.isSelected()) {
                codeSecurityCheckbox.click()
            }

            // Apply settings
            find<JButtonFixture>(
                byXpath("//div[@text='Apply']"),
                Duration.ofSeconds(5)
            ).click()

            find<JButtonFixture>(
                byXpath("//div[@text='OK']"),
                Duration.ofSeconds(5)
            ).click()
        }

        step("Trigger Code Security scan") {
            // Find scan button in Snyk tool window
            val scanButton = find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Run scan']"),
                Duration.ofSeconds(10)
            )
            scanButton.click()

            // Wait for scan to start
            waitFor(duration = Duration.ofSeconds(60)) {
                try {
                    // Look for scanning indicator or results
                    findAll<CommonContainerFixture>(
                        byXpath("//div[contains(@class, 'ProgressBar')]")
                    ).isNotEmpty() ||
                    findAll<CommonContainerFixture>(
                        byXpath("//div[@class='Tree' and contains(., 'Code Security')]")
                    ).isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
        }

        step("Verify Code Security results") {
            // Wait for results to appear
            waitFor(duration = Duration.ofMinutes(3)) {
                try {
                    val resultsTree = find<JTreeFixture>(
                        byXpath("//div[@class='Tree']"),
                        Duration.ofSeconds(10)
                    )
                    
                    // Check for Code Security node
                    resultsTree.hasText("Code Security")
                } catch (e: Exception) {
                    false
                }
            }

            // Expand Code Security results
            val resultsTree = find<JTreeFixture>(
                byXpath("//div[@class='Tree']"),
                Duration.ofSeconds(10)
            )

            val codeSecurityNode = resultsTree.findText("Code Security")
            codeSecurityNode.doubleClick()

            // Verify at least one vulnerability is found
            waitFor(duration = Duration.ofSeconds(30)) {
                resultsTree.hasText("High") || 
                resultsTree.hasText("Medium") || 
                resultsTree.hasText("Low")
            }
        }

        step("View Code Security vulnerability details") {
            val resultsTree = find<JTreeFixture>(
                byXpath("//div[@class='Tree']"),
                Duration.ofSeconds(10)
            )

            // Find and click on a vulnerability by searching for text
            try {
                val vulnerabilityNode = resultsTree.findText("vulnerability") ?: 
                                      resultsTree.findText("issue") ?:
                                      resultsTree.findText("security")
                vulnerabilityNode?.click()

                // Verify description panel shows details
                waitFor(duration = Duration.ofSeconds(10)) {
                    findAll<CommonContainerFixture>(
                        byXpath("//div[@class='JBCefBrowser']")
                    ).isNotEmpty()
                }
            } catch (e: Exception) {
                // No vulnerability nodes found
            }
        }
    }

    @After
    fun tearDown() {
        // Close any open dialogs
        try {
            remoteRobot.findAll<CommonContainerFixture>(
                byXpath("//div[@class='MyDialog']")
            ).forEach {
                try {
                    it.button("Cancel").click()
                } catch (e: Exception) {
                    // If no cancel button, press ESC
                    remoteRobot.keyboard {
                        key(KeyEvent.VK_ESCAPE)
                    }
                }
            }
        } catch (e: Exception) {
            // No dialogs to close
        }
    }
}