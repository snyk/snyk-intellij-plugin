package snyk.iac

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.project.Project
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class IacIgnoreServiceTest {
    private val expectedWorkingDirectory = "testDir"
    private val expectedApiToken = UUID.randomUUID().toString()
    private val mockRunner = mockk<ConsoleCommandRunner>()
    private val project = mockk<Project>()
    private val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)

    @Before
    fun setUp() {
        clearAllMocks()
        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic(ProgressManager::class)
        mockkStatic(ApplicationManager::class)
        val application = mockk<Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns application
        every { application.isUnitTestMode } returns true
        every { application.isHeadlessEnvironment } returns true
        every { ProgressManager.getInstance() } returns ProgressManagerImpl()
        every { pluginSettings() } returns settings
        every { getCliFile() } returns File.createTempFile("cliTestTmpFile", ".tmp")
        every { project.basePath } returns expectedWorkingDirectory
        every { settings.token } returns expectedApiToken
        every { settings.getAdditionalParameters() } returns ""
    }

    @Test
    fun `ignore should call the cli ignore functionality with the issue given`() {
        val issue = IacIssue(
            "IaCTestIssueId", "", "", "", "", 0, "", "", null, emptyList(), emptyList()
        )
        val expectedCommands = listOf(
            getCliFile().absolutePath,
            "--json",
            "--DISABLE_ANALYTICS",
            "ignore",
            "--id=${issue.id}"
        )
        val expectedOutput = "mockedOutput"

        every {
            mockRunner.execute(expectedCommands, expectedWorkingDirectory, expectedApiToken, project)
        } returns expectedOutput

        val actualOutput = IacIgnoreService(mockRunner, project, emptyList()).ignore(issue)

        verify(exactly = 1) {
            mockRunner.execute(expectedCommands, expectedWorkingDirectory, expectedApiToken, project)
        }
        assertEquals(expectedOutput, actualOutput)
    }
}
