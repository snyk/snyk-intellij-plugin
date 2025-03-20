package snyk.iac

import com.intellij.testFramework.LightPlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getIacService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.setupDummyCliFile
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import java.util.concurrent.CompletableFuture

class IacServiceTest : LightPlatformTestCase() {

    private val lsMock: LanguageServer = mockk()
    private val wholeProjectJson = getResourceAsString("iac-test-results/infrastructure-as-code-goof.json")
    private val singleFileJson = getResourceAsString("iac-test-results/fargate.json")

    private fun getResourceAsString(resourceName: String): String = javaClass.classLoader
        .getResource(resourceName)!!.readText(Charsets.UTF_8)

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        resetSettings(project)
        removeDummyCliFile()

        val settingsStateService = pluginSettings()

        settingsStateService.ignoreUnknownCA = false
        settingsStateService.usageAnalyticsEnabled = true
        settingsStateService.token = ""
        settingsStateService.customEndpointUrl = ""
        settingsStateService.cliVersion = ""
        settingsStateService.lastCheckDate = null
        settingsStateService.organization = ""

        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.languageServer = lsMock
        languageServerWrapper.isInitialized = true
    }

    override fun tearDown() {
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
    }

    private val iacService: IacScanService
        get() = getIacService(project) ?: throw IllegalStateException("IaC service should be available")

    @Test
    fun testBuildCliCommandsListWithDefaults() {
        setupDummyCliFile()

        val cliCommands = iacService.buildCliCommandsList_TEST_ONLY(listOf("fake_cli_command"))

        assertTrue(cliCommands.contains(getCliFile().absolutePath))
        assertTrue(cliCommands.contains("fake_cli_command"))
        assertTrue(cliCommands.contains("--json"))
    }

    @Test
    fun testScanWithErrorResult() {
        setupDummyCliFile()

        val errorMsg = "Some error here"
        val errorPath = "/Users/user/Desktop/example-npm-project"
        val expectedResult = mapOf(
            Pair(
                "stdOut", """
            {
              "error": "$errorMsg",
              "path": "$errorPath"
            }
        """.trimIndent()
            )
        )

        every { lsMock.workspaceService.executeCommand(any()) } returns CompletableFuture.completedFuture(expectedResult)

        val iacResult = iacService.scan()

        assertFalse(iacResult.isSuccessful())
        assertEquals(errorMsg, iacResult.getFirstError()!!.message)
        assertEquals(errorPath, iacResult.getFirstError()!!.path)
    }

    @Test
    fun testScanWithSuccessfulIacResult() {
        setupDummyCliFile()

        val expectedResult = mapOf(Pair("stdOut", wholeProjectJson))

        every { lsMock.workspaceService.executeCommand(any()) } returns CompletableFuture.completedFuture(expectedResult)

        val iacResult = iacService.scan()

        assertTrue(iacResult.isSuccessful())

        val issuesForFile = iacResult.allCliIssues!!.first()
        assertEquals("cloudformationconfig", issuesForFile.packageManager)

        val vulnerabilityId = issuesForFile.infrastructureAsCodeIssues.first().id

        assertEquals("SNYK-CC-TF-53", vulnerabilityId)
    }

    @Test
    fun testConvertRawCliStringToIacResult() {
        val singleObjectIacResult = iacService.convertRawCliStringToCliResult(singleFileJson)
        assertTrue(singleObjectIacResult.isSuccessful())

        val arrayObjectIacResult = iacService.convertRawCliStringToCliResult(wholeProjectJson)
        assertTrue(arrayObjectIacResult.isSuccessful())

        val errorMsg = "Some error here"
        val errorPath = "/Users/user/Desktop/example-npm-project"
        val jsonErrorIacResult = iacService.convertRawCliStringToCliResult(
            """
                    {
                      "error": "$errorMsg",
                      "path": "$errorPath"
                    }
                """.trimIndent()
        )
        assertFalse(jsonErrorIacResult.isSuccessful())
        assertEquals(errorMsg, jsonErrorIacResult.getFirstError()!!.message)
        assertEquals(errorPath, jsonErrorIacResult.getFirstError()!!.path)

        val rawErrorIacResult = iacService.convertRawCliStringToCliResult(errorMsg)
        assertFalse(rawErrorIacResult.isSuccessful())
        assertEquals(errorMsg, rawErrorIacResult.getFirstError()!!.message)
        assertEquals(project.basePath, rawErrorIacResult.getFirstError()!!.path)
    }

    @Test
    fun testConvertRawCliStringToIacResultWithEmptyRawString() {
        val cliResult = iacService.convertRawCliStringToCliResult("")
        assertFalse(cliResult.isSuccessful())
    }

    @Test
    fun testConvertRawCliStringToIacResultWithMissformedJson() {
        val cliResult = iacService.convertRawCliStringToCliResult(
            """
                    {
                      "ok": false,
                      "error": ["could not be","array here"],
                      "path": "some/path/here"
                    }
                """.trimIndent()
        )
        assertFalse(cliResult.isSuccessful())
    }

    @Test
    fun testConvertRawCliStringToIacResultFieldsInitialisation() {
        val iacResult = iacService.convertRawCliStringToCliResult(wholeProjectJson)
        assertTrue(iacResult.isSuccessful())

        touchAllFields(iacResult)
    }

    private fun touchAllFields(iacResultToCheck: IacResult) {
        iacResultToCheck.allCliIssues?.forEach {
            it.targetFile
            it.packageManager
            it.uniqueCount
            it.infrastructureAsCodeIssues.forEach { iacIssue ->
                with(iacIssue) {
                    id
                    title
                    getSeverity()
                    publicId
                    documentation
                    lineNumber
                    issue
                    impact
                    resolve
                    references
                    path
                }
            }
        }
    }
}
