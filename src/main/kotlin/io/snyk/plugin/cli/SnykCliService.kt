package io.snyk.plugin.cli

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCliFile
import io.snyk.plugin.settings.SnykApplicationSettingsStateService
import org.apache.log4j.Logger
import org.jetbrains.annotations.CalledInAwt
import java.io.File
import java.util.Objects.nonNull
import java.util.regex.Pattern

/**
 * Wrap work with Snyk CLI.
 */
@Service
class SnykCliService(val project: Project) {

    private var consoleCommandRunner: ConsoleCommandRunner? = null

    private val logger: Logger = Logger.getLogger(SnykCliService::class.java)

    fun isCliInstalled(): Boolean {
        logger.info("Check whether Snyk CLI is installed")

        return checkIsCliInstalledManuallyByUser() || checkIsCliInstalledAutomaticallyByPlugin()
    }

    fun checkIsCliInstalledManuallyByUser(): Boolean {
        logger.debug("Check whether Snyk CLI is installed by user.")

        val commands: List<String> = listOf(getCliCommandName(), "--version")

        return try {
            val consoleResultStr = getConsoleCommandRunner().execute(commands)

            val pattern = Pattern.compile("^\\d+\\.\\d+\\.\\d+")
            val matcher = pattern.matcher(consoleResultStr.trim())

            matcher.matches()
        } catch(exception: Exception) {
            logger.error(exception.message)

            false
        }
    }

    fun checkIsCliInstalledAutomaticallyByPlugin(): Boolean {
        logger.debug("Check whether Snyk CLI is installed by plugin automatically.")

        return getCliFile().exists()
    }

    @CalledInAwt
    fun scan(): CliResult {
        val commands = buildCliCommandsList(getApplicationSettingsStateService())

        val snykResultJsonStr = getConsoleCommandRunner().execute(commands, project.basePath!!)

        return Gson().fromJson(snykResultJsonStr, CliResult::class.java)
    }

    /**
     * Build list of commands for run Snyk CLI command.
     *
     * @param settings           - Snyk Application (and project) settings
     *
     * @return List<String>
     */
    fun buildCliCommandsList(settings: SnykApplicationSettingsStateService): List<String> {
        logger.info("Enter buildCliCommandsList")

        val commands: MutableList<String> = mutableListOf()
        commands.add(getCliCommandName())
        commands.add("--json")

        val customEndpoint = settings.getCustomEndpointUrl()

        if (nonNull(customEndpoint) && customEndpoint.isNotEmpty()) {
            commands.add("--api=$customEndpoint")
        }

        if (settings.isIgnoreUnknownCA()) {
            commands.add("--insecure")
        }

        val organization = settings.getOrganization()

        if (nonNull(organization) && organization.isNotEmpty()) {
            commands.add("--org=$organization")
        }

        val additionalParameters = settings.getAdditionalParameters(project)

        if (nonNull(additionalParameters) && additionalParameters.isNotEmpty()) {
            commands.add(additionalParameters)
        }

        commands.add("test")

        logger.info("Cli parameters: $commands")

        return commands.toList()
    }

    fun setConsoleCommandRunner(newRunner: ConsoleCommandRunner?) {
        this.consoleCommandRunner = newRunner
    }

    private fun getCliCommandName(): String = if (SystemInfo.isWindows) "snyk.cmd" else "snyk"

    private fun getConsoleCommandRunner(): ConsoleCommandRunner {
        if (nonNull(consoleCommandRunner)) {
            return consoleCommandRunner!!
        }

        return ConsoleCommandRunner()
    }

    fun isPackageJsonExists(): Boolean = File(project.basePath!!).exists()
}
