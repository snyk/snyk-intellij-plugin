package snyk.oss

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getOssService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykProjectSettingsStateService
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test

class OssServiceTest : LightPlatformTestCase() {

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

    private val ossService: OssService
        get() = getOssService(project) ?: throw IllegalStateException("OSS service should be available")

    @Test
    fun testBuildCliCommandsListWithDefaults() {
        setupDummyCliFile()

        val cliCommands = ossService.buildCliCommandsList_TEST_ONLY(listOf("fake_cli_command"))

        assertTrue(cliCommands.contains(getCliFile().absolutePath))
        assertTrue(cliCommands.contains("fake_cli_command"))
        assertTrue(cliCommands.contains("--json"))
    }

    @Test
    fun testBuildCliCommandsListWithFileParameter() {
        setupDummyCliFile()

        project.service<SnykProjectSettingsStateService>().additionalParameters = "--file=package.json"

        val cliCommands = ossService.buildCliCommandsList_TEST_ONLY(listOf("fake_cli_command"))

        assertTrue(cliCommands.contains("--file=package.json"))
    }

    @Test
    fun testBuildCliCommandsListWithMultiAdditionalParameters() {
        setupDummyCliFile()

        project.service<SnykProjectSettingsStateService>().additionalParameters =
            "--file=package.json --configuration-matching='iamaRegex' --sub-project=snyk"

        val cliCommands = ossService.buildCliCommandsList_TEST_ONLY(listOf("fake_cli_command"))

        assertTrue(cliCommands.contains("--file=package.json"))
        assertTrue(cliCommands.contains("--configuration-matching='iamaRegex'"))
        assertTrue(cliCommands.contains("--sub-project=snyk"))
    }

    @Test
    fun testGroupVulnerabilities() {
        val cli = ossService

        val cliResult = cli.convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-test.json"))

        val cliGroupedResult = cliResult.allCliIssues!!.first().toGroupedResult()

        assertEquals(21, cliGroupedResult.uniqueCount)
        assertEquals(36, cliGroupedResult.pathsCount)
    }

    @Test
    fun testGroupVulnerabilitiesForGoof() {
        val cli = ossService

        val cliResult = cli.convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-goof-test.json"))

        val cliGroupedResult = cliResult.allCliIssues!!.first().toGroupedResult()

        assertEquals(78, cliGroupedResult.uniqueCount)
        assertEquals(310, cliGroupedResult.pathsCount)
    }

    @Test
    fun testScanWithErrorResult() {
        setupDummyCliFile()

        val mockRunner = mockk<ConsoleCommandRunner>()

        every {
            mockRunner.execute(listOf(getCliFile().absolutePath, "test", "--json"), project.basePath!!, project = project)
        } returns """
              {
                  "ok": false,
                  "error": "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
                  "path": "/Users/user/Desktop/example-npm-project"
              }
            """.trimIndent()

        ossService.setConsoleCommandRunner(mockRunner)

        val cliResult = ossService.scan()

        assertFalse(cliResult.isSuccessful())
        assertEquals(
            "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
            cliResult.getFirstError()!!.message)
        assertEquals("/Users/user/Desktop/example-npm-project", cliResult.getFirstError()!!.path)
    }

    @Test
    fun testScanWithSuccessfulCliResult() {
        setupDummyCliFile()

        val mockRunner = mockk<ConsoleCommandRunner>()

        every {
            mockRunner.execute(listOf(getCliFile().absolutePath, "test", "--json"), project.basePath!!, project = project)
        } returns getResourceAsString("group-vulnerabilities-test.json")

        ossService.setConsoleCommandRunner(mockRunner)

        val cliResult = ossService.scan()

        assertTrue(cliResult.isSuccessful())

        assertEquals("npm", cliResult.allCliIssues!!.first().packageManager)

        val vulnerabilityIds = cliResult.allCliIssues!!.first().vulnerabilities.map { it.id }

        assertTrue(vulnerabilityIds.contains("SNYK-JS-DOTPROP-543489"))
        assertTrue(vulnerabilityIds.contains("SNYK-JS-OPEN-174041"))
        assertTrue(vulnerabilityIds.contains("npm:qs:20140806-1"))
    }

    @Test
    fun testScanWithLicenseVulnerabilities() {
        setupDummyCliFile()

        val mockRunner = mockk<ConsoleCommandRunner>()

        every {
            mockRunner.execute(listOf(getCliFile().absolutePath, "test", "--json"), project.basePath!!, project = project)
        } returns getResourceAsString("licence-vulnerabilities.json")

        ossService.setConsoleCommandRunner(mockRunner)

        val cliResult = ossService.scan()

        assertTrue(cliResult.isSuccessful())

        val vulnerabilityIds = cliResult.allCliIssues!!.first().vulnerabilities.map { it.id }

        assertTrue(vulnerabilityIds.contains("snyk:lic:pip:nltk:Apache-2.0"))
        assertTrue(vulnerabilityIds.contains("snyk:lic:pip:six:MIT"))
    }

    @Test
    fun testConvertRawCliStringToCliResult() {

        val sigleObjectCliResult = ossService
            .convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-test.json"))
        assertTrue(sigleObjectCliResult.isSuccessful())

        val arrayObjectCliResult = ossService
            .convertRawCliStringToCliResult(getResourceAsString("vulnerabilities-array-cli-result.json"))
        assertTrue(arrayObjectCliResult.isSuccessful())

        val jsonErrorCliResult = ossService.convertRawCliStringToCliResult("""
                    {
                      "ok": false,
                      "error": "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
                      "path": "/Users/user/Desktop/example-npm-project"
                    }
                """.trimIndent())
        assertFalse(jsonErrorCliResult.isSuccessful())
        assertEquals("Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
            jsonErrorCliResult.getFirstError()!!.message)
        assertEquals("/Users/user/Desktop/example-npm-project", jsonErrorCliResult.getFirstError()!!.path)

        val rawErrorCliResult = ossService.convertRawCliStringToCliResult("""
                    Missing node_modules folder: we can't test without dependencies. Please run 'npm install' first.
                """.trimIndent())
        assertFalse(rawErrorCliResult.isSuccessful())
        assertEquals("Missing node_modules folder: we can't test without dependencies. Please run 'npm install' first.",
            rawErrorCliResult.getFirstError()!!.message)
        assertEquals(project.basePath, rawErrorCliResult.getFirstError()!!.path)
    }

    @Test
    fun testConvertRawCliStringWithLicenseVulnsToCliResult() {

        val rawMissedFixedInFieldCliString = getResourceAsString("licence-vulnerabilities.json")
        val cliResult = ossService.convertRawCliStringToCliResult(rawMissedFixedInFieldCliString)
        assertTrue(cliResult.isSuccessful())
        assertNotNull(cliResult.allCliIssues?.find { it ->
            it.vulnerabilities
                .any { vulnerability -> vulnerability.fixedIn == null }
        })

        touchAllFields(cliResult)
    }

    @Test
    fun testConvertRawCliStringToCliResultWithEmptyRawString() {
        val cliResult = ossService.convertRawCliStringToCliResult("")
        assertFalse(cliResult.isSuccessful())
    }

    @Test
    fun testConvertMissformedErrorAsArrayJson() {
        val cliResult = ossService.convertRawCliStringToCliResult("""
                    {
                      "ok": false,
                      "error": ["could not be","array here"],
                      "path": "some/path/here"
                    }
                """.trimIndent())
        assertFalse(cliResult.isSuccessful())
        assertTrue(cliResult.getFirstError()!!.message.contains(
            "Expected a string but was BEGIN_ARRAY"
        ))
    }

    @Test
    fun testConvertMissformedErrorPathTagJson() {
        val cliResult2 = ossService.convertRawCliStringToCliResult("""
                    {
                      "ok": false,
                      "error": "error",
                      "path_not_provided": ""
                    }
                """.trimIndent())
        assertFalse(cliResult2.isSuccessful())
        assertTrue(cliResult2.getFirstError()!!.message.contains(
            "Parameter specified as non-null is null: method snyk.common.SnykError.<init>, parameter path"
        ))
    }

    @Test
    fun testConvertMissformedResultArrayJson() {
        val cliResult1 = ossService.convertRawCliStringToCliResult("""
            {
              "vulnerabilities": "SHOULD_BE_ARRAY_HERE",
              "packageManager": "npm",
              "displayTargetFile": "package-lock.json",
              "path": "D:\\TestProjects\\goof"
            }
            """.trimIndent())
        assertFalse(cliResult1.isSuccessful())
        assertTrue(cliResult1.getFirstError()!!.message.contains(
            "Expected BEGIN_ARRAY but was STRING"
        ))
    }

    @Test
    fun testConvertMissformedResultNestedJson() {
        val cliResult2 = ossService.convertRawCliStringToCliResult("""
            {
              "vulnerabilities": [
                {
                  "wrong-tag-here": "bla-bla-bla"
                }
              ],
              "packageManager": "npm",
              "displayTargetFile": "package-lock.json",
              "path": "D:\\TestProjects\\goof"
            }
            """.trimIndent())
        assertFalse(cliResult2.isSuccessful())
        assertTrue(cliResult2.getFirstError()!!.message.contains(
            "Parameter specified as non-null is null: method snyk.oss.Vulnerability.copy, parameter id"
        ))
    }

    @Test
    fun testConvertMissformedResultRootTagJson() {
        val rawCliString = getResourceAsString("missformed-vulnerabilities-test.json")
        val cliResult3 = ossService.convertRawCliStringToCliResult(rawCliString)
        assertFalse(cliResult3.isSuccessful())
        assertTrue(cliResult3.getFirstError()!!.message.contains(
            "Parameter specified as non-null is null: method snyk.oss.OssVulnerabilitiesForFile.copy, parameter displayTargetFile"
        ))
    }

    @Test
    fun testConvertRawCliStringToCliResultFieldsInitialisation() {
        val rawCliString = getResourceAsString("group-vulnerabilities-goof-test.json")
        val cliResult = ossService.convertRawCliStringToCliResult(rawCliString)
        assertTrue(cliResult.isSuccessful())

        touchAllFields(cliResult)
    }

    private fun touchAllFields(ossResultToCheck: OssResult) {
        ossResultToCheck.allCliIssues?.forEach {
            it.sanitizedTargetFile
            it.packageManager
            it.uniqueCount
            it.vulnerabilities.forEach { vuln ->
                with(vuln) {
                    id
                    license
                    identifiers?.cve
                    identifiers?.cwe
                    title
                    description
                    language
                    packageManager
                    packageName
                    getSeverity()
                    name
                    version
                    exploit
                    cvssV3
                    cvssScore
                    fixedIn
                    from
                    upgradePath
                }
            }
        }
    }

    private fun getResourceAsString(resourceName: String): String = javaClass.classLoader
        .getResource(resourceName)!!.readText(Charsets.UTF_8)
}
