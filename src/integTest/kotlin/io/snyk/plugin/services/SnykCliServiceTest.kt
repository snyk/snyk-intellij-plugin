package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCli
import io.snyk.plugin.getCliFile
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test
import org.mockito.Mockito
import java.io.File

class SnykCliServiceTest : LightPlatformTestCase() {

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        getCli(project).setConsoleCommandRunner(null)

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
        val cli = getCli(project)

        val cliResult = cli.convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-test.json"), "")

        val cliGroupedResult = cliResult.allCliVulnerabilities!!.first().toCliGroupedResult()

        assertEquals(21, cliGroupedResult.uniqueCount)
        assertEquals(36, cliGroupedResult.pathsCount)
    }

    @Test
    fun testGroupVulnerabilitiesForGoof() {
        val cli = getCli(project)

        val cliResult = cli.convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-goof-test.json"), "")

        val cliGroupedResult = cliResult.allCliVulnerabilities!!.first().toCliGroupedResult()

        assertEquals(78, cliGroupedResult.uniqueCount)
        assertEquals(310, cliGroupedResult.pathsCount)
    }

    @Test
    fun testIsPackageJsonExists() {
        val projectDirectory = File(project.basePath!!)

        if (!projectDirectory.exists()) {
            projectDirectory.mkdir()
        }

        val packageJsonFile = File(projectDirectory, "package.json")

        packageJsonFile.createNewFile()

        assertTrue(getCli(project).isPackageJsonExists())

        packageJsonFile.delete()
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

        getCli(project).setConsoleCommandRunner(mockRunner)

        val cliResult = getCli(project).scan()

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

        getCli(project).setConsoleCommandRunner(mockRunner)

        val cliResult = getCli(project).scan()

        assertTrue(cliResult.isSuccessful())

        assertEquals("npm", cliResult.allCliVulnerabilities!!.first().packageManager)

        val vulnerabilityIds = cliResult.allCliVulnerabilities!!.first().vulnerabilities.map { it.id }

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

        getCli(project).setConsoleCommandRunner(mockRunner)

        val cliResult = getCli(project).scan()

        assertTrue(cliResult.isSuccessful())

        val vulnerabilityIds = cliResult.allCliVulnerabilities!!.first().vulnerabilities.map { it.id }

        assertTrue(vulnerabilityIds.contains("snyk:lic:pip:nltk:Apache-2.0"))
        assertTrue(vulnerabilityIds.contains("snyk:lic:pip:six:MIT"))
    }

    @Test
    fun testIsCliInstalledFailed() {
        val cliFile = getCliFile()

        if (cliFile.exists()) {
            cliFile.delete()
        }

        val isCliInstalled = getCli(project).isCliInstalled()

        assertFalse(isCliInstalled)
    }

    @Test
    fun testIsCliInstalledAutomaticallyByPluginFailed() {
        val cliFile = getCliFile()

        if (cliFile.exists()) {
            cliFile.delete()
        }

        val isCliInstalled = getCli(project).isCliInstalled()

        assertFalse(isCliInstalled)
    }

    @Test
    fun testIsCliInstalledAutomaticallyByPluginSuccess() {
        val cliFile = getCliFile()

        if (!cliFile.exists()) {
            cliFile.createNewFile()
        }

        val isCliInstalled = getCli(project).isCliInstalled()

        assertTrue(isCliInstalled)

        cliFile.delete()
    }

    @Test
    fun testIsCliInstalledSuccess() {
        setupDummyCliFile()

        val isCliInstalled = getCli(project).isCliInstalled()

        assertTrue(isCliInstalled)
    }

    @Test
    fun testBuildCliCommandsListForMaven() {
        setupDummyCliFile()

        val defaultCommands =  getCli(project).buildCliCommandsList(getApplicationSettingsStateService())

        assertEquals(getCliFile().absolutePath, defaultCommands[0])
        assertEquals("test", defaultCommands[1])
        assertEquals("--json", defaultCommands[2])
    }

    @Test
    fun testBuildCliCommandsListWithCustomEndpointParameter() {
        setupDummyCliFile()

        val settingsStateService = getApplicationSettingsStateService()

        settingsStateService.customEndpointUrl = "https://app.snyk.io/api"

        val defaultCommands = getCli(project).buildCliCommandsList(settingsStateService)

        assertEquals(getCliFile().absolutePath, defaultCommands[0])
        assertEquals("test", defaultCommands[1])
        assertEquals("--json", defaultCommands[2])
        assertEquals("--API=https://app.snyk.io/api", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithInsecureParameter() {
        setupDummyCliFile()

        val settings = getApplicationSettingsStateService()

        settings.ignoreUnknownCA = true

        val defaultCommands = getCli(project).buildCliCommandsList(settings)

        assertEquals(getCliFile().absolutePath, defaultCommands[0])
        assertEquals("test", defaultCommands[1])
        assertEquals("--json", defaultCommands[2])
        assertEquals("--insecure", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithOrganizationParameter() {
        setupDummyCliFile()

        val settingsStateService = getApplicationSettingsStateService()
        settingsStateService.organization = "test-org"

        val defaultCommands = getCli(project).buildCliCommandsList(settingsStateService)

        assertEquals(getCliFile().absolutePath, defaultCommands[0])
        assertEquals("test", defaultCommands[1])
        assertEquals("--json", defaultCommands[2])
        assertEquals("--org=test-org", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithDisableAnalyticsParameter() {
        setupDummyCliFile()
        val settings = getApplicationSettingsStateService()
        settings.usageAnalyticsEnabled = false

        val cliCommands = getCli(project).buildCliCommandsList(settings)

        assertEquals(getCliFile().absolutePath, cliCommands[0])
        assertEquals("test", cliCommands[1])
        assertEquals("--json", cliCommands[2])
        assertEquals("--DISABLE_ANALYTICS", cliCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithFileParameter() {
        setupDummyCliFile()

        project.service<SnykProjectSettingsStateService>().additionalParameters = "--file=package.json"

        val defaultCommands = getCli(project).buildCliCommandsList(getApplicationSettingsStateService())

        assertEquals(getCliFile().absolutePath, defaultCommands[0])
        assertEquals("test", defaultCommands[1])
        assertEquals("--json", defaultCommands[2])
        assertEquals("--file=package.json", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithAllParameter() {
        setupDummyCliFile()

        val settingsStateService = getApplicationSettingsStateService()

        settingsStateService.token = "0000-1111-2222-3333"
        settingsStateService.customEndpointUrl = "https://app.snyk.io/api"
        settingsStateService.organization = "test-org"
        settingsStateService.ignoreUnknownCA = true

        project.service<SnykProjectSettingsStateService>().additionalParameters = "--file=package.json"

        val defaultCommands = getCli(project).buildCliCommandsList(settingsStateService)

        assertEquals(getCliFile().absolutePath, defaultCommands[0])
        assertEquals("test", defaultCommands[1])
        assertEquals("--json", defaultCommands[2])
        assertEquals("--API=https://app.snyk.io/api", defaultCommands[3])
        assertEquals("--insecure", defaultCommands[4])
        assertEquals("--org=test-org", defaultCommands[5])
        assertEquals("--file=package.json", defaultCommands[6])
    }

    @Test
    fun testBuildCliCommandsListWithMultiAdditionalParameters() {
        setupDummyCliFile()

        val settingsStateService = getApplicationSettingsStateService()

        settingsStateService.token = "0000-1111-2222-3333"
        settingsStateService.customEndpointUrl = "https://app.snyk.io/api"
        settingsStateService.organization = "test-org"
        settingsStateService.ignoreUnknownCA = true

        project.service<SnykProjectSettingsStateService>().additionalParameters =
            "--file=package.json --configuration-matching='iamaRegex' --sub-project=snyk"

        val defaultCommands = getCli(project).buildCliCommandsList(settingsStateService)

        assertEquals(getCliFile().absolutePath, defaultCommands[0])
        assertEquals("test", defaultCommands[1])
        assertEquals("--json", defaultCommands[2])
        assertEquals("--API=https://app.snyk.io/api", defaultCommands[3])
        assertEquals("--insecure", defaultCommands[4])
        assertEquals("--org=test-org", defaultCommands[5])
        assertEquals("--file=package.json", defaultCommands[6])
        assertEquals("--configuration-matching='iamaRegex'", defaultCommands[7])
        assertEquals("--sub-project=snyk", defaultCommands[8])
    }

    @Test
    fun testCheckIsCliInstalledAutomaticallyByPluginSuccessful() {
        val cliFile = getCliFile()

        if (!cliFile.exists()) {
            cliFile.createNewFile()
        }

        assertTrue(getCli(project).checkIsCliInstalledAutomaticallyByPlugin())

        cliFile.delete()
    }

    @Test
    fun testCheckIsCliInstalledAutomaticallyByPluginFailed() {
        val cliFile = getCliFile()

        if (cliFile.exists()) {
            cliFile.delete()
        }

        assertFalse(getCli(project).checkIsCliInstalledAutomaticallyByPlugin())
    }

    @Test
    fun testConvertRawCliStringToCliResult() {
        val cli = getCli(project)

        val sigleObjectCliResult = cli
            .convertRawCliStringToCliResult(getResourceAsString("group-vulnerabilities-test.json"), "")
        assertTrue(sigleObjectCliResult.isSuccessful())

        val arrayObjectCliResult = cli
            .convertRawCliStringToCliResult(getResourceAsString("vulnerabilities-array-cli-result.json"), "")
        assertTrue(arrayObjectCliResult.isSuccessful())

        val jsonErrorCliResult = cli.convertRawCliStringToCliResult("""
                    {
                      "ok": false,
                      "error": "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
                      "path": "/Users/user/Desktop/example-npm-project"
                    }
                """.trimIndent(), "")
        assertFalse(jsonErrorCliResult.isSuccessful())
        assertEquals("Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
            jsonErrorCliResult.error!!.message)
        assertEquals("/Users/user/Desktop/example-npm-project", jsonErrorCliResult.error!!.path)

        val rawErrorCliResult = cli.convertRawCliStringToCliResult("""
                    Missing node_modules folder: we can't test without dependencies. Please run 'npm install' first.
                """.trimIndent(), "")
        assertFalse(rawErrorCliResult.isSuccessful())
        assertEquals("Missing node_modules folder: we can't test without dependencies. Please run 'npm install' first.",
            rawErrorCliResult.error!!.message)
        assertEquals("", rawErrorCliResult.error!!.path)
    }

    @Test
    fun testConvertRawCliStringWithLicenseVulnsToCliResult() {
        val cli = getCli(project)

        val rawMissedFixedInFieldCliString = getResourceAsString("licence-vulnerabilities.json")
        val cliResult = cli.convertRawCliStringToCliResult(rawMissedFixedInFieldCliString, "")
        assertTrue(cliResult.isSuccessful())
        assertNotNull(cliResult.allCliVulnerabilities?.find { it.vulnerabilities.any { it.fixedIn == null } })

        touchAllFields(cliResult)
    }

    @Test
    fun testConvertRawCliStringToCliResultFieldsInitialisation() {
        val cli = getCli(project)

        val rawCliString = getResourceAsString("group-vulnerabilities-goof-test.json")
        val cliResult = cli.convertRawCliStringToCliResult(rawCliString, "")
        assertTrue(cliResult.isSuccessful())

        touchAllFields(cliResult)
    }

    private  fun touchAllFields(cliResultToCheck: CliResult) {
        cliResultToCheck.allCliVulnerabilities?.forEach {
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
