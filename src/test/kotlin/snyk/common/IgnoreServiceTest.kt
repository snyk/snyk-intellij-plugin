package snyk.common

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.cli.CliNotExistsException
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

class IgnoreServiceTest {
    private val expectedWorkingDirectory = "testDir"
    private val expectedApiToken = UUID.randomUUID().toString()
    private val commandRunner = mockk<ConsoleCommandRunner>()
    private val project = mockk<Project>()
    private val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")

        every { pluginSettings() } returns settings
        every { getCliFile() } returns File.createTempFile("cliTestTmpFile", ".tmp")
        every { project.basePath } returns expectedWorkingDirectory
        every { settings.token } returns expectedApiToken
        every { settings.getAdditionalParameters() } returns ""
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `ignore should call the cli ignore with the issue given`() {
        val issueId = "IssueId"
        val expectedCommands = listOf(
            getCliFile().absolutePath,
            "ignore",
            "--id=$issueId",
            "--json",
            "--DISABLE_ANALYTICS"
        )
        val expectedOutput = ""
        val cut = IgnoreService(project)
        cut.setConsoleCommandRunner(commandRunner)

        every {
            commandRunner.execute(expectedCommands, expectedWorkingDirectory, expectedApiToken, project)
        } returns expectedOutput

        cut.ignore(issueId)

        verify(exactly = 1) {
            commandRunner.execute(expectedCommands, expectedWorkingDirectory, expectedApiToken, project)
        }
    }

    @Test(expected = IgnoreException::class)
    fun `ignore should call the cli ignore with the issue given and throw exception if output not empty`() {
        val issueId = "IssueId"
        val expectedCommands = listOf(
            getCliFile().absolutePath,
            "ignore",
            "--id=$issueId",
            "--json",
            "--DISABLE_ANALYTICS"
        )
        val expectedOutput = "unexpected output"
        val cut = IgnoreService(project)
        cut.setConsoleCommandRunner(commandRunner)

        every {
            commandRunner.execute(expectedCommands, expectedWorkingDirectory, expectedApiToken, project)
        } returns expectedOutput

        try {
            cut.ignore(issueId)
        } finally {
            verify(exactly = 1) {
                commandRunner.execute(expectedCommands, expectedWorkingDirectory, expectedApiToken, project)
            }
        }
    }

    @Test(expected = IgnoreException::class)
    fun `ignore should call the cli ignore with the issue given and throw exception cli throws error`() {
        val issueId = "IssueId"
        val expectedCommands = listOf(
            getCliFile().absolutePath,
            "ignore",
            "--id=$issueId",
            "--json",
            "--DISABLE_ANALYTICS"
        )
        val cut = IgnoreService(project)
        cut.setConsoleCommandRunner(commandRunner)

        every {
            commandRunner.execute(expectedCommands, expectedWorkingDirectory, expectedApiToken, project)
        } throws CliNotExistsException()

        try {
            cut.ignore(issueId)
        } finally {
            verify(exactly = 1) {
                commandRunner.execute(expectedCommands, expectedWorkingDirectory, expectedApiToken, project)
            }
        }
    }
}
