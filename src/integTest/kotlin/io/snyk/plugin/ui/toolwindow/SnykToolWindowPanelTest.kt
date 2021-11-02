package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.Severity
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getIacService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.setupDummyCliFile
import io.snyk.plugin.ui.actions.SnykTreeMediumSeverityFilterAction
import org.junit.Test
import snyk.iac.IacIssue
import snyk.iac.ui.toolwindow.IacIssueTreeNode

class SnykToolWindowPanelTest : HeavyPlatformTestCase() {

    private val iacGoofJson = getResourceAsString("iac-test-results/infrastructure-as-code-goof.json")

    private fun getResourceAsString(resourceName: String): String = javaClass.classLoader
        .getResource(resourceName)!!.readText(Charsets.UTF_8)

    override fun setUp() {
        super.setUp()
        clearAllMocks()
        unmockkAll()
        resetSettings(project)
        setupDummyCliFile()
    }

    override fun tearDown() {
        clearAllMocks()
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
    }

    @Test
    fun testSeverityFilterForIacResult() {
        // pre-test setup
        val settings = pluginSettings()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = true

        val toolWindowPanel = project.service<SnykToolWindowPanel>()

        val isIacEnabledRegistryValue = Registry.get("snyk.preview.iac.enabled")
        val isIacEnabledOldValue = isIacEnabledRegistryValue.asBoolean()
        isIacEnabledRegistryValue.setValue(true)

        // mock IaC results
        val mockRunner = mockk<ConsoleCommandRunner>()
        every {
            mockRunner.execute(
                listOf(getCliFile().absolutePath, "iac", "test", "--json"),
                project.basePath!!,
                project = project
            )
        } returns (iacGoofJson)

        getIacService(project).setConsoleCommandRunner(mockRunner)

        // actual test run

        project.service<SnykTaskQueueService>().scan()

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val rootIacIssuesTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        fun isMediumSeverityShown(): Boolean = rootIacIssuesTreeNode.children().asSequence()
            .flatMap { it.children().asSequence() }
            .any {
                it is IacIssueTreeNode &&
                    it.userObject is IacIssue &&
                    (it.userObject as IacIssue).severity == Severity.MEDIUM
            }

        assertTrue("Medium severity IaC results should be shown by default", isMediumSeverityShown())

        val mediumSeverityFilterAction =
            ActionManager.getInstance().getAction("io.snyk.plugin.ui.actions.SnykTreeMediumSeverityFilterAction")
                as SnykTreeMediumSeverityFilterAction
        mediumSeverityFilterAction.setSelected(TestActionEvent(), false)

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertFalse("Medium severity IaC results should NOT be shown after filtering", isMediumSeverityShown())

        // restore modified Registry value
        isIacEnabledRegistryValue.setValue(isIacEnabledOldValue)
    }
}
