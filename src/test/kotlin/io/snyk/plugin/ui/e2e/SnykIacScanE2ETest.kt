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
 * E2E test for Infrastructure as Code (IaC) scanning functionality
 * Tests the complete workflow of scanning IaC configurations for security issues
 */
class SnykIacScanE2ETest {
    private lateinit var remoteRobot: RemoteRobot
    private val testProjectPath = System.getProperty("test.project.path", ".")

    @Before
    fun setUp() {
        remoteRobot = RemoteRobot("http://127.0.0.1:8082")
    }

    @Test
    fun `should perform IaC scan on terraform and kubernetes files`() = with(remoteRobot) {
        step("Open terraform-goof project from VCS") {
            try {
                // Check if we're on the welcome screen
                val welcomeFrame = find<CommonContainerFixture>(byXpath("//div[@class='FlatWelcomeFrame']"))
                
                // Click "Clone Repository" button
                val getFromVcsButton = welcomeFrame.find<JButtonFixture>(
                    byXpath("//div[@text='Clone Repository']")
                )
                getFromVcsButton.click()
                
                // Wait for VCS dialog
                Thread.sleep(2000)
                
                // Find URL input field and enter terraform-goof repository
                val urlField = find<CommonContainerFixture>(byXpath("//div[@class='TextFieldWithBrowseButton']"))
                urlField.click()
                keyboard {
                    enterText("https://github.com/snyk-labs/terraform-goof")
                }
                
                // Click Clone button
                val cloneButton = find<JButtonFixture>(byXpath("//div[@text='Clone']"))
                cloneButton.click()
                
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

        step("Enable IaC scanning in settings") {
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

            settingsTree.findText("Snyk").click()

            // Enable IaC scanning
            val iacCheckbox = find<JCheckboxFixture>(
                byXpath("//div[@text='Snyk Infrastructure as Code issues']"),
                Duration.ofSeconds(5)
            )

            if (!iacCheckbox.isSelected()) {
                iacCheckbox.click()
            }

            // Apply and close settings
            find<JButtonFixture>(
                byXpath("//div[@text='Apply']"),
                Duration.ofSeconds(5)
            ).click()

            find<JButtonFixture>(
                byXpath("//div[@text='OK']"),
                Duration.ofSeconds(5)
            ).click()
        }

        step("Trigger IaC scan") {
            // Find and click scan button
            val scanButton = find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Run scan']"),
                Duration.ofSeconds(10)
            )
            scanButton.click()

            // Wait for scan to start
            waitFor(duration = Duration.ofSeconds(60)) {
                try {
                    findAll<CommonContainerFixture>(
                        byXpath("//div[contains(@class, 'ProgressBar')]")
                    ).isNotEmpty() ||
                    findAll<CommonContainerFixture>(
                        byXpath("//div[@class='Tree' and contains(., 'Infrastructure as Code')]")
                    ).isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }
        }

        step("Verify IaC scan results") {
            // Wait for results
            waitFor(duration = Duration.ofMinutes(2)) {
                try {
                    val resultsTree = find<JTreeFixture>(
                        byXpath("//div[@class='Tree']"),
                        Duration.ofSeconds(10)
                    )
                    resultsTree.hasText("Infrastructure as Code")
                } catch (e: Exception) {
                    false
                }
            }

            val resultsTree = find<JTreeFixture>(
                byXpath("//div[@class='Tree']"),
                Duration.ofSeconds(10)
            )

            // Expand IaC results
            val iacNode = resultsTree.findText("Infrastructure as Code")
            iacNode.doubleClick()

            // Verify Terraform and Kubernetes issues are found
            waitFor(duration = Duration.ofSeconds(30)) {
                resultsTree.hasText(".tf") || 
                resultsTree.hasText(".yaml") ||
                resultsTree.hasText(".yml")
            }
        }

        step("View IaC issue details") {
            val resultsTree = find<JTreeFixture>(
                byXpath("//div[@class='Tree']"),
                Duration.ofSeconds(10)
            )

            // Find Terraform or Kubernetes configuration issues
            val configNodes = listOf(
                resultsTree.findText(".tf"),
                resultsTree.findText(".yaml"),
                resultsTree.findText(".yml"),
                resultsTree.findText("configuration")
            ).filterNotNull()

            if (configNodes.isNotEmpty()) {
                // Click on first configuration file with issues
                configNodes.firstOrNull()?.click()

                // Wait for issue details
                waitFor(duration = Duration.ofSeconds(10)) {
                    findAll<CommonContainerFixture>(
                        byXpath("//div[@class='Tree']//div[contains(@text, 'Security')]")
                    ).isNotEmpty()
                }

                // Click on a specific issue
                val issueNodes = listOf(
                    resultsTree.findText("Security"),
                    resultsTree.findText("misconfiguration")
                ).filterNotNull()

                if (issueNodes.isNotEmpty()) {
                    issueNodes.firstOrNull()?.click()

                    // Verify issue description panel
                    waitFor(duration = Duration.ofSeconds(10)) {
                        findAll<CommonContainerFixture>(
                            byXpath("//div[@class='JBCefBrowser']")
                        ).isNotEmpty()
                    }
                }
            }
        }

        step("Test IaC issue filtering") {
            // Find filter button
            val filterButton = find<JButtonFixture>(
                byXpath("//div[@tooltiptext='Filter issues']"),
                Duration.ofSeconds(10)
            )
            filterButton.click()

            // Wait for filter menu
            waitFor(duration = Duration.ofSeconds(5)) {
                findAll<CommonContainerFixture>(
                    byXpath("//div[@class='HeavyWeightWindow']")
                ).isNotEmpty()
            }

            // Select High severity only
            val highSeverityCheckbox = find<JCheckboxFixture>(
                byXpath("//div[@text='High']"),
                Duration.ofSeconds(5)
            )

            if (!highSeverityCheckbox.isSelected()) {
                highSeverityCheckbox.click()
            }

            // Deselect other severities
            listOf("Critical", "Medium", "Low").forEach { severity ->
                try {
                    val checkbox = find<JCheckboxFixture>(
                        byXpath("//div[@text='$severity']"),
                        Duration.ofSeconds(2)
                    )
                    if (checkbox.isSelected()) {
                        checkbox.click()
                    }
                } catch (e: Exception) {
                    // Severity might not exist
                }
            }

            // Close filter menu
            keyboard {
                key(KeyEvent.VK_ESCAPE)
            }

            // Verify filtered results
            val resultsTree = find<JTreeFixture>(
                byXpath("//div[@class='Tree']"),
                Duration.ofSeconds(10)
            )

            assertTrue("Should show filtered IaC results", 
                resultsTree.hasText("High") || resultsTree.hasText("0 issues"))
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