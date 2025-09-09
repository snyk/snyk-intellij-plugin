package io.snyk.plugin.ui.e2e

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.JButtonFixture
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
 * E2E test for project trust management functionality
 * Tests the workflow of trusting projects before scanning
 */
class SnykProjectTrustE2ETest {
    private lateinit var remoteRobot: RemoteRobot
    private val untrustedProjectPath = System.getProperty("untrusted.project.path", "./test-projects/untrusted")

    @Before
    fun setUp() {
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
    }

    @Test
    fun `should show trust dialog for untrusted project`() = with(remoteRobot) {
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

        step("Open untrusted project") {
            // Open File menu
            find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']")).apply {
                keyboard {
                    hotKey(KeyEvent.VK_ALT, KeyEvent.VK_F)
                }
            }

            // Click Open
            find<CommonContainerFixture>(byXpath("//div[@text='Open...']")).click()

            // Wait for file dialog
            waitFor(duration = Duration.ofSeconds(5)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@class='FileChooserDialogImpl']")
                ).isNotEmpty()
            }

            // Enter untrusted project path
            keyboard {
                enterText(untrustedProjectPath)
                enter()
            }
        }

        step("Open Snyk tool window") {
            val ideFrame = find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))

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

        step("Verify trust panel is displayed") {
            // Wait for trust panel
            waitFor(duration = Duration.ofSeconds(10)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@class='SnykAuthPanel' or contains(@class, 'TrustPanel')]")
                ).isNotEmpty()
            }

            // Look for trust message
            val trustPanel = find<CommonContainerFixture>(
                byXpath("//div[@class='SnykAuthPanel' or contains(@class, 'TrustPanel')]"),
                Duration.ofSeconds(5)
            )

            // Verify trust button exists
            val trustButton = trustPanel.find<JButtonFixture>(
                byXpath("//div[@text='Trust project and scan' or @text='Trust this project']"),
                Duration.ofSeconds(5)
            )

            assertTrue("Trust button should be enabled", trustButton.isEnabled())
        }

        step("Trust the project") {
            // Click trust button
            val trustButton = find<JButtonFixture>(
                byXpath("//div[@text='Trust project and scan' or @text='Trust this project']"),
                Duration.ofSeconds(5)
            )
            trustButton.click()

            // Wait for scan to start or main panel to appear
            waitFor(duration = Duration.ofSeconds(30)) {
                try {
                    // Either scan starts or main panel appears
                    findAll<CommonContainerFixture>(
                        byXpath("//div[contains(@class, 'ProgressBar')]")
                    ).isNotEmpty() ||
                    findAll<CommonContainerFixture>(
                        byXpath("//div[@class='Tree']")
                    ).isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
        }

        step("Verify project is now trusted") {
            // Trigger scan to verify trust
            val scanButton = find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Run scan']"),
                Duration.ofSeconds(10)
            )
            scanButton.click()

            // Scan should start without trust prompt
            waitFor(duration = Duration.ofSeconds(20)) {
                try {
                    findAll<CommonContainerFixture>(
                        byXpath("//div[contains(@class, 'ProgressBar')]")
                    ).isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }

            // Verify no trust panel appears
            val trustPanels = findAll<CommonContainerFixture>(
                byXpath("//div[@class='SnykAuthPanel' or contains(@class, 'TrustPanel')]")
            )
            
            // If trust panels exist, they should not contain trust buttons
            trustPanels.forEach { panel ->
                val trustButtons = panel.findAll<JButtonFixture>(
                    byXpath("//div[@text='Trust project and scan' or @text='Trust this project']")
                )
                assertTrue("Should not show trust buttons after trusting", trustButtons.isEmpty())
            }
        }
    }

    @Test
    fun `should respect do not ask again option`() = with(remoteRobot) {
        step("Open settings") {
            keyboard {
                if (System.getProperty("os.name").contains("Mac")) {
                    hotKey(KeyEvent.VK_META, KeyEvent.VK_COMMA)
                } else {
                    hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_S)
                }
            }
        }

        step("Navigate to trust settings") {
            waitFor(duration = Duration.ofSeconds(10)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@class='MyDialog' and contains(@title.key, 'settings')]")
                ).isNotEmpty()
            }

            // Find settings tree
            val settingsTree = find<CommonContainerFixture>(
                byXpath("//div[@class='SettingsTreeView']"),
                Duration.ofSeconds(10)
            )

            // Navigate to trust settings (might be under Tools or Snyk)
            try {
                settingsTree.findText("Trust").click()
            } catch (e: Exception) {
                // Try under Snyk
                settingsTree.findText("Snyk").click()
                Thread.sleep(500)
                settingsTree.findText("Trust").click()
            }
        }

        step("Enable auto-trust option") {
            // Find checkbox for auto-trust
            val autoTrustCheckbox = find<CommonContainerFixture>(
                byXpath("//div[contains(@text, 'Trust all projects') or contains(@text, 'Do not ask')]"),
                Duration.ofSeconds(5)
            )

            // Enable if not already enabled
            if (!autoTrustCheckbox.hasText("selected") && !autoTrustCheckbox.hasText("true")) {
                autoTrustCheckbox.click()
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

        step("Verify new projects are auto-trusted") {
            // Open a new untrusted project
            keyboard {
                hotKey(KeyEvent.VK_ALT, KeyEvent.VK_F)
            }

            find<CommonContainerFixture>(byXpath("//div[@text='Open...']")).click()

            waitFor(duration = Duration.ofSeconds(5)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@class='FileChooserDialogImpl']")
                ).isNotEmpty()
            }

            keyboard {
                enterText("./test-projects/another-untrusted")
                enter()
            }

            // Open Snyk tool window
            val ideFrame = find<CommonContainerFixture>(byXpath("//div[@class='IdeFrameImpl']"))
            val snykToolWindowButton = ideFrame.find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Snyk' and @class='StripeButton']"),
                Duration.ofSeconds(5)
            )
            snykToolWindowButton.click()

            // Should not show trust panel
            Thread.sleep(2000) // Give time for trust panel to appear if it would

            val trustPanels = findAll<CommonContainerFixture>(
                byXpath("//div[@text='Trust project and scan' or @text='Trust this project']")
            )

            assertTrue("Should not show trust prompt with auto-trust enabled", trustPanels.isEmpty())
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