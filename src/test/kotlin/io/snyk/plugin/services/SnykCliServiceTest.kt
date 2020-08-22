package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCli
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getCliNotInstalledRunner
import org.junit.Test
import java.io.File

class SnykCliServiceTest : LightPlatformTestCase() {

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        getCli(project).setConsoleCommandRunner(null)

        val settingsStateService = getApplicationSettingsStateService()

        settingsStateService.setIgnoreUnknownCA(false)
        settingsStateService.setCustomEndpointUrl("")
        settingsStateService.setCliVersion("")
        settingsStateService.setLastCheckDate(null)
        settingsStateService.setOrganization("")

        project.service<SnykProjectSettingsStateService>().setAdditionalParameters("")
    }

    @Test
    fun testGroupVulnerabilities() {
        val cli = getCli(project)

        val cliResult = cli.jsonToCliResult(getResourceAsString("group-vulnerabilities-test.json"))

        val cliGroupedResult = cliResult.toCliGroupedResult()

        assertEquals(21, cliGroupedResult.uniqueCount)
        assertEquals(36, cliGroupedResult.pathsCount)
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
        getCli(project).setConsoleCommandRunner(object : ConsoleCommandRunner() {
            override fun execute(commands: List<String>, workDirectory: String): String {
                return """
                    {
                      "ok": false,
                      "error": "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
                      "path": "/Users/user/Desktop/example-npm-project"
                    }
                """.trimIndent()
            }
        })

        val cliResult = getCli(project).scan()

        assertFalse(cliResult.isSuccessful())
        assertEquals(
            "Missing node_modules folder: we can't test without dependencies.\nPlease run 'npm install' first.",
            cliResult.error!!.message)
        assertEquals("/Users/user/Desktop/example-npm-project", cliResult.error!!.path)
    }

    @Test
    fun testScanWithSuccessfulCliResult() {
        getCli(project).setConsoleCommandRunner(object : ConsoleCommandRunner() {
            override fun execute(commands: List<String>, workDirectory: String): String {
                return getResourceAsString("group-vulnerabilities-test.json")
            }
        })

        val cliResult = getCli(project).scan()

        assertTrue(cliResult.isSuccessful())


        val vulnerabilityIds = cliResult.vulnerabilities.map { it.id }

        assertTrue(vulnerabilityIds.contains("SNYK-JS-DOTPROP-543489"))
        assertTrue(vulnerabilityIds.contains("SNYK-JS-OPEN-174041"))
        assertTrue(vulnerabilityIds.contains("npm:qs:20140806-1"))
    }

    @Test
    fun testIsCliInstalledFailed() {
        val cliFile = getCliFile()

        if (cliFile.exists()) {
            cliFile.delete()
        }

        val cli = getCli(project)

        cli.setConsoleCommandRunner(getCliNotInstalledRunner())

        val isCliInstalled = cli.isCliInstalled()

        assertFalse(isCliInstalled)
    }

    @Test
    fun testIsCliInstalledAutomaticallyByPluginFailed() {
        val cli = getCli(project)

        cli.setConsoleCommandRunner(getCliNotInstalledRunner())

        val cliFile = getCliFile()

        if (cliFile.exists()) {
            cliFile.delete()
        }

        val isCliInstalled = cli.isCliInstalled()

        assertFalse(isCliInstalled)
    }

    @Test
    fun testIsCliInstalledAutomaticallyByPluginSuccess() {
        val cli = getCli(project)

        cli.setConsoleCommandRunner(getCliNotInstalledRunner())

        val cliFile = getCliFile()

        if (!cliFile.exists()) {
            cliFile.createNewFile()
        }

        val isCliInstalled = cli.isCliInstalled()

        assertTrue(isCliInstalled)

        cliFile.delete()
    }

    @Test
    fun testIsCliInstalledSuccess() {
        val cli = getCli(project)

        cli.setConsoleCommandRunner(object: ConsoleCommandRunner() {
            override fun execute(commands: List<String>, workDirectory: String): String {
                return "1.290.2"
            }
        })

        val isCliInstalled = cli.isCliInstalled()

        assertTrue(isCliInstalled)
    }

    @Test
    fun testBuildCliCommandsListForMaven() {
        val defaultCommands =  getCli(project).buildCliCommandsList(getApplicationSettingsStateService())

        assertTrue(defaultCommands[0] == "snyk" || defaultCommands[0] == "snyk.cmd")

        assertEquals("--json", defaultCommands[1])
        assertEquals("test", defaultCommands[2])
    }

    @Test
    fun testBuildCliCommandsListWithCustomEndpointParameter() {
        val settingsStateService = getApplicationSettingsStateService()

        settingsStateService.setCustomEndpointUrl("https://app.snyk.io/api")

        val defaultCommands = getCli(project).buildCliCommandsList(settingsStateService)

        assertTrue(defaultCommands[0] == "snyk" || defaultCommands[0] == "snyk.cmd")
        assertEquals("--json", defaultCommands[1])
        assertEquals("--api=https://app.snyk.io/api", defaultCommands[2])
        assertEquals("test", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithInsecureParameter() {
        val settings = getApplicationSettingsStateService()

        settings.setIgnoreUnknownCA(true)

        val defaultCommands = getCli(project).buildCliCommandsList(settings)

        assertTrue(defaultCommands[0] == "snyk" || defaultCommands[0] == "snyk.cmd")
        assertEquals("--json", defaultCommands[1])
        assertEquals("--insecure", defaultCommands[2])
        assertEquals("test", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithOrganizationParameter() {
        val settingsStateService = getApplicationSettingsStateService()
        settingsStateService.setOrganization("test-org")

        val defaultCommands = getCli(project).buildCliCommandsList(settingsStateService)

        assertTrue(defaultCommands[0] == "snyk" || defaultCommands[0] == "snyk.cmd")
        assertEquals("--json", defaultCommands[1])
        assertEquals("--org=test-org", defaultCommands[2])
        assertEquals("test", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithFileParameter() {
        project.service<SnykProjectSettingsStateService>().setAdditionalParameters("--file=package.json")

        val defaultCommands = getCli(project).buildCliCommandsList(getApplicationSettingsStateService())

        assertTrue(defaultCommands[0] == "snyk" || defaultCommands[0] == "snyk.cmd")
        assertEquals("--json", defaultCommands[1])
        assertEquals("--file=package.json", defaultCommands[2])
        assertEquals("test", defaultCommands[3])
    }

    @Test
    fun testBuildCliCommandsListWithAllParameter() {
        val settingsStateService = getApplicationSettingsStateService()

        settingsStateService.setCustomEndpointUrl("https://app.snyk.io/api")
        settingsStateService.setOrganization("test-org")
        settingsStateService.setIgnoreUnknownCA(true)

        project.service<SnykProjectSettingsStateService>().setAdditionalParameters("--file=package.json")

        val defaultCommands = getCli(project).buildCliCommandsList(settingsStateService)

        assertTrue(defaultCommands[0] == "snyk" || defaultCommands[0] == "snyk.cmd")
        assertEquals("--json", defaultCommands[1])
        assertEquals("--api=https://app.snyk.io/api", defaultCommands[2])
        assertEquals("--insecure", defaultCommands[3])
        assertEquals("--org=test-org", defaultCommands[4])
        assertEquals("--file=package.json", defaultCommands[5])
        assertEquals("test", defaultCommands[6])
    }

    @Test
    fun testCheckIsCliInstalledManuallyByUserSuccessful() {
        val cli = getCli(project)

        cli.setConsoleCommandRunner(object: ConsoleCommandRunner() {
            override fun execute(commands: List<String>, workDirectory: String): String {
                return "1.381.1"
            }
        })

        assertTrue(cli.checkIsCliInstalledManuallyByUser())
    }

    @Test
    fun testCheckIsCliInstalledManuallyByUserFailed() {
        val cli = getCli(project)

        cli.setConsoleCommandRunner(getCliNotInstalledRunner())

        assertFalse(cli.checkIsCliInstalledManuallyByUser())
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

    private fun getResourceAsString(resourceName: String): String = javaClass.classLoader
        .getResource(resourceName)!!.readText(Charsets.UTF_8)
}
