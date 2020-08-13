package io.snyk.plugin.cli

import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.getCli
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getCliNotInstalledRunner
import org.junit.After
import org.junit.Test

class SnykCliServiceTest : LightPlatformTestCase() {

    @After
    fun afterTest() {
        getCli(project).setConsoleCommandRunner(null)
    }

    @Test
    fun testIsCliInstalledFailed() {
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
}
