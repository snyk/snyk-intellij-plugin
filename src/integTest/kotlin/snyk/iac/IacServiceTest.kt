package snyk.iac

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getIacService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykProjectSettingsStateService
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test

class IacServiceTest : LightPlatformTestCase() {

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

        project.service<SnykProjectSettingsStateService>().additionalParameters = ""
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

        val cliCommands = iacService.buildCliCommandsList(listOf("fake_cli_command"))

        assertTrue(cliCommands.contains(getCliFile().absolutePath))
        assertTrue(cliCommands.contains("fake_cli_command"))
        assertTrue(cliCommands.contains("--json"))
    }

    @Test
    fun testBuildCliCommandsListWithDisableAnalyticsParameter() {
        setupDummyCliFile()
        pluginSettings().usageAnalyticsEnabled = false

        val cliCommands = iacService.buildCliCommandsList(listOf("fake_cli_command"))

        assertFalse(cliCommands.contains("--DISABLE_ANALYTICS"))
    }

    @Test
    fun testBuildCliCommandsListWithFileParameter() {
        setupDummyCliFile()

        project.service<SnykProjectSettingsStateService>().additionalParameters = "--file=package.json"

        val cliCommands = iacService.buildCliCommandsList(listOf("fake_cli_command"))

        assertFalse(cliCommands.contains("--file=package.json"))
    }

    @Test
    fun testScanWithErrorResult() {
        setupDummyCliFile()

        val mockRunner = mockk<ConsoleCommandRunner>()

        val errorMsg = "Some error here"
        val errorPath = "/Users/user/Desktop/example-npm-project"

        every {
            mockRunner.execute(
                listOf(getCliFile().absolutePath, "iac", "test", "--json"),
                project.basePath!!,
                project = project
            )
        } returns """
            {
              "error": "$errorMsg",
              "path": "$errorPath"
            }
        """.trimIndent()

        iacService.setConsoleCommandRunner(mockRunner)

        val iacResult = iacService.scan()

        assertFalse(iacResult.isSuccessful())
        assertEquals(errorMsg, iacResult.error!!.message)
        assertEquals(errorPath, iacResult.error!!.path)
    }

    @Test
    fun testScanWithSuccessfulIacResult() {
        setupDummyCliFile()

        val mockRunner = mockk<ConsoleCommandRunner>()

        every {
            mockRunner.execute(
                listOf(getCliFile().absolutePath, "iac", "test", "--json"),
                project.basePath!!,
                project = project
            )
        } returns (wholeProjectJson)

        iacService.setConsoleCommandRunner(mockRunner)

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
        val jsonErrorIacResult = iacService.convertRawCliStringToCliResult("""
                    {
                      "error": "$errorMsg",
                      "path": "$errorPath"
                    }
                """.trimIndent())
        assertFalse(jsonErrorIacResult.isSuccessful())
        assertEquals(errorMsg, jsonErrorIacResult.error!!.message)
        assertEquals(errorPath, jsonErrorIacResult.error!!.path)

        val rawErrorIacResult = iacService.convertRawCliStringToCliResult(errorMsg)
        assertFalse(rawErrorIacResult.isSuccessful())
        assertEquals(errorMsg, rawErrorIacResult.error!!.message)
        assertEquals(project.basePath, rawErrorIacResult.error!!.path)
    }

    @Test
    fun testConvertRawCliStringToIacResultWithEmptyRawString() {
        val cliResult = iacService.convertRawCliStringToCliResult("")
        assertFalse(cliResult.isSuccessful())
    }

    @Test
    fun testConvertRawCliStringToIacResultWithMissformedJson() {
        val cliResult = iacService.convertRawCliStringToCliResult("""
                    {
                      "ok": false,
                      "error": ["could not be","array here"],
                      "path": "some/path/here"
                    }
                """.trimIndent())
        assertFalse(cliResult.isSuccessful())
    }

    @Test
    fun testConvertRawCliStringToIacResultFieldsInitialisation() {
        val iacResult = iacService.convertRawCliStringToCliResult(wholeProjectJson)
        assertTrue(iacResult.isSuccessful())

        touchAllFields(iacResult)
    }

    private fun touchAllFields(ossResultToCheck: IacResult) {
        ossResultToCheck.allCliIssues?.forEach {
            it.targetFile
            it.packageManager
            it.uniqueCount
            it.infrastructureAsCodeIssues.forEach { iacIssue ->
                with(iacIssue) {
                    id
                    title
                    severity
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
