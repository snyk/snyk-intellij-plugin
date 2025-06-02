package io.snyk.plugin.ui.jcef

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.resetSettings
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.services.LanguageServer
import snyk.common.annotator.SnykIaCAnnotator
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.commands.COMMAND_EXECUTE_CLI
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture

class IgnoreInFileHandlerTest : BasePlatformTestCase() {
    private lateinit var ignorer: IgnoreInFileHandler
    private val fileName = "fargate.json"
    val lsMock = mockk<LanguageServer>()

    override fun getTestDataPath(): String {
        val resource = SnykIaCAnnotator::class.java.getResource("/iac-test-results")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        languageServerWrapper.languageServer = lsMock
        languageServerWrapper.isInitialized = true
        ignorer = IgnoreInFileHandler(project)
    }

    fun `test issue should be ignored in file`() {
        every { lsMock.workspaceService.executeCommand(any()) } returns CompletableFuture.completedFuture(null)
        val filePath = this.getTestDataPath()+ File.separator + fileName;
        ignorer.applyIgnoreInFileAndSave("SNYK-CC-TF-61", filePath )
        val projectBasePath = project.basePath ?: "";

        // Expected args for executeCommandParams
        val args: List<String> = arrayListOf(projectBasePath, "ignore", "--id=SNYK-CC-TF-61", "--path=${filePath}")

        val executeCommandParams = ExecuteCommandParams (COMMAND_EXECUTE_CLI, args);
        verify { lsMock.workspaceService.executeCommand(executeCommandParams) }
    }
}
