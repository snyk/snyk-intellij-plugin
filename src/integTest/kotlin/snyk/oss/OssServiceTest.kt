package snyk.oss

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getOssService
import io.snyk.plugin.services.SnykProjectSettingsStateService
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test
import org.mockito.Mockito

class OssServiceTest : LightPlatformTestCase() {

    override fun setUp() {
        super.setUp()

        val settingsStateService = getApplicationSettingsStateService()

        settingsStateService.ignoreUnknownCA = false
        settingsStateService.usageAnalyticsEnabled = true
        settingsStateService.token = ""
        settingsStateService.customEndpointUrl = ""
        settingsStateService.cliVersion = ""
        settingsStateService.lastCheckDate = null
        settingsStateService.organization = ""

        project.service<SnykProjectSettingsStateService>().additionalParameters = ""
    }

    @Test
    fun testGroupVulnerabilities() {
        val cli = getOssService(project)

        val cliResult = cli.convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-test.json"))

        val cliGroupedResult = cliResult.allCliIssues!!.first().toGroupedResult()

        assertEquals(21, cliGroupedResult.uniqueCount)
        assertEquals(36, cliGroupedResult.pathsCount)
    }

    @Test
    fun testGroupVulnerabilitiesForGoof() {
        val cli = getOssService(project)

        val cliResult = cli.convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-goof-test.json"))

        val cliGroupedResult = cliResult.allCliIssues!!.first().toGroupedResult()

        assertEquals(78, cliGroupedResult.uniqueCount)
        assertEquals(310, cliGroupedResult.pathsCount)
    }

    @Test
    fun testScanWithErrorResult() {
        setupDummyCliFile()

        val mockRunner = Mockito.mock(ConsoleCommandRunner::class.java)

        Mockito
            .`when`(mockRunner.execute(listOf(getCliFile().absolutePath, "test", "--json"), project.basePath!!, project = project))
            .thenReturn("""
                    {
                      "ok": false,
                      "error": "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
                      "path": "/Users/user/Desktop/example-npm-project"
                    }
                """.trimIndent())

        getOssService(project).setConsoleCommandRunner(mockRunner)

        val cliResult = getOssService(project).scan()

        assertFalse(cliResult.isSuccessful())
        assertEquals(
            "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
            cliResult.error!!.message)
        assertEquals("/Users/user/Desktop/example-npm-project", cliResult.error!!.path)
    }

    @Test
    fun testScanWithSuccessfulCliResult() {
        setupDummyCliFile()

        val mockRunner = Mockito.mock(ConsoleCommandRunner::class.java)

        Mockito
            .`when`(mockRunner.execute(listOf(getCliFile().absolutePath, "test", "--json"), project.basePath!!, project = project))
            .thenReturn(getResourceAsString("group-vulnerabilities-test.json"))

        getOssService(project).setConsoleCommandRunner(mockRunner)

        val cliResult = getOssService(project).scan()

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

        val mockRunner = Mockito.mock(ConsoleCommandRunner::class.java)

        Mockito
            .`when`(mockRunner.execute(listOf(getCliFile().absolutePath, "test", "--json"), project.basePath!!, project = project))
            .thenReturn(getResourceAsString("licence-vulnerabilities.json"))

        getOssService(project).setConsoleCommandRunner(mockRunner)

        val cliResult = getOssService(project).scan()

        assertTrue(cliResult.isSuccessful())

        val vulnerabilityIds = cliResult.allCliIssues!!.first().vulnerabilities.map { it.id }

        assertTrue(vulnerabilityIds.contains("snyk:lic:pip:nltk:Apache-2.0"))
        assertTrue(vulnerabilityIds.contains("snyk:lic:pip:six:MIT"))
    }

    @Test
    fun testConvertRawCliStringToCliResult() {
        val cli = getOssService(project)

        val sigleObjectCliResult = cli
            .convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-test.json"))
        assertTrue(sigleObjectCliResult.isSuccessful())

        val arrayObjectCliResult = cli
            .convertRawCliStringToCliResult(getResourceAsString("vulnerabilities-array-cli-result.json"))
        assertTrue(arrayObjectCliResult.isSuccessful())

        val jsonErrorCliResult = cli.convertRawCliStringToCliResult("""
                    {
                      "ok": false,
                      "error": "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
                      "path": "/Users/user/Desktop/example-npm-project"
                    }
                """.trimIndent())
        assertFalse(jsonErrorCliResult.isSuccessful())
        assertEquals("Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
            jsonErrorCliResult.error!!.message)
        assertEquals("/Users/user/Desktop/example-npm-project", jsonErrorCliResult.error!!.path)

        val rawErrorCliResult = cli.convertRawCliStringToCliResult("""
                    Missing node_modules folder: we can't test without dependencies. Please run 'npm install' first.
                """.trimIndent())
        assertFalse(rawErrorCliResult.isSuccessful())
        assertEquals("Missing node_modules folder: we can't test without dependencies. Please run 'npm install' first.",
            rawErrorCliResult.error!!.message)
        assertEquals(project.basePath, rawErrorCliResult.error!!.path)
    }

    @Test
    fun testConvertRawCliStringWithLicenseVulnsToCliResult() {
        val cli = getOssService(project)

        val rawMissedFixedInFieldCliString = getResourceAsString("licence-vulnerabilities.json")
        val cliResult = cli.convertRawCliStringToCliResult(rawMissedFixedInFieldCliString)
        assertTrue(cliResult.isSuccessful())
        assertNotNull(cliResult.allCliIssues?.find { it.vulnerabilities.any { it.fixedIn == null } })

        touchAllFields(cliResult)
    }

    @Test
    fun testConvertRawCliStringToCliResultFieldsInitialisation() {
        val cli = getOssService(project)

        val rawCliString = getResourceAsString("group-vulnerabilities-goof-test.json")
        val cliResult = cli.convertRawCliStringToCliResult(rawCliString)
        assertTrue(cliResult.isSuccessful())

        touchAllFields(cliResult)
    }

    private  fun touchAllFields(ossResultToCheck: OssResult) {
        ossResultToCheck.allCliIssues?.forEach {
            it.displayTargetFile
            it.packageManager
            it.projectName
            it.uniqueCount
            it.vulnerabilities.forEach { vuln ->
                with(vuln){
                    id
                    license
                    identifiers?.CVE
                    identifiers?.CWE
                    title
                    description
                    language
                    packageManager
                    packageName
                    severity
                    name
                    version
                    exploit
                    CVSSv3
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
