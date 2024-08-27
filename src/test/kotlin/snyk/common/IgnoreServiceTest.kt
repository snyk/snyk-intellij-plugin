package snyk.common

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getCliFile
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import junit.framework.TestCase.assertEquals
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.commands.COMMAND_EXECUTE_CLI
import snyk.iac.IacIssue
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

class IgnoreServiceTest {
    private val applicationMock = mockk<Application>()
    private val expectedWorkingDirectory = "testDir"
    private val expectedApiToken = UUID.randomUUID().toString()
    private val project = mockk<Project>()
    private val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
    private val lsMock = mockk<LanguageServer>()

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns applicationMock
        every { applicationMock.getService(SnykPluginDisposable::class.java)} returns mockk(relaxed = true)
        every { applicationMock.isDisposed } returns false
        every { pluginSettings() } returns settings
        every { getCliFile() } returns File.createTempFile("cliTestTmpFile", ".tmp")
        every { isCliInstalled() } returns true
        every { project.basePath } returns expectedWorkingDirectory
        every { settings.token } returns expectedApiToken
        every { settings.getAdditionalParameters() } returns ""

        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.languageServer = lsMock
        languageServerWrapper.isInitialized = true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `ignore should call the cli ignore with the issue given`() {
        val issueId = "IssueId"
        val expectedCommands = listOf(
            "ignore",
            "--id=$issueId"
        )
        val expectedOutput = ""
        val cut = IgnoreService(project)

        val params = ExecuteCommandParams(COMMAND_EXECUTE_CLI, listOf(expectedWorkingDirectory, *expectedCommands.toTypedArray()))

        every {
            lsMock.workspaceService.executeCommand(params)
        } returns CompletableFuture.completedFuture(mapOf(Pair("stdOut",expectedOutput)))

        cut.ignore(issueId)

        verify { lsMock.workspaceService.executeCommand(params) }
    }

    @Test
    fun `ignoreInstance should call the cli ignore with the instance information`() {
        val issueId = "IssueId"
        val path = "InstancePath"
        val expectedCommands = listOf(
            "ignore",
            "--id=$issueId",
            "--path=$path"
        )
        val expectedOutput = ""
        val cut = IgnoreService(project)

        val params = ExecuteCommandParams(COMMAND_EXECUTE_CLI, listOf(expectedWorkingDirectory, *expectedCommands.toTypedArray()))

        every {
            lsMock.workspaceService.executeCommand(params)
        } returns CompletableFuture.completedFuture(mapOf(Pair("stdOut",expectedOutput)))

        cut.ignoreInstance(issueId, path)

        verify(exactly = 1) { lsMock.workspaceService.executeCommand(params) }
    }

    @Test
    fun `buildPath should construct the right path`() {
        val issuePath = listOf("[DocId:1]", "spec")
        val issue = IacIssue(
            id = "testId",
            title = "",
            severity = "",
            publicId = "",
            documentation = "",
            lineNumber = 0,
            issue = "",
            impact = "",
            resolve = "",
            references = emptyList(), path = issuePath
        )
        val cut = IgnoreService(project)
        val targetFile = "production/deployment.yaml"
        val expectedPath = targetFile + " > " + issuePath.joinToString(" > ")

        val actualPath = cut.buildPath(issue, targetFile)

        assertEquals(expectedPath, actualPath)
    }

    @Test(expected = IgnoreException::class)
    fun `ignore should call the cli ignore with the issue given and throw exception if output not empty`() {
        val issueId = "IssueId"
        val expectedCommands = listOf(
            "ignore",
            "--id=$issueId"
        )
        val expectedOutput = "unexpected output"
        val cut = IgnoreService(project)
        val params = ExecuteCommandParams(COMMAND_EXECUTE_CLI, listOf(expectedWorkingDirectory, *expectedCommands.toTypedArray()))

        every {
            lsMock.workspaceService.executeCommand(params)
        } returns CompletableFuture.completedFuture(mapOf(Pair("stdOut",expectedOutput)))

        try {
            cut.ignore(issueId)
        } finally {
            verify(exactly = 1) { lsMock.workspaceService.executeCommand(params) }
        }
    }

    @Test(expected = IgnoreException::class)
    fun `ignore should call the cli ignore with the issue given and throw exception if cli throws error`() {
        val issueId = "IssueId"
        val expectedCommands = listOf(
            "ignore",
            "--id=$issueId"
        )
        val cut = IgnoreService(project)

        val params = ExecuteCommandParams(COMMAND_EXECUTE_CLI, listOf(expectedWorkingDirectory, *expectedCommands.toTypedArray()))

        every { lsMock.workspaceService.executeCommand(params) } throws RuntimeException("testException")

        try {
            cut.ignore(issueId)
        } finally {
            verify(exactly = 1) { lsMock.workspaceService.executeCommand(params) }
        }
    }
}
