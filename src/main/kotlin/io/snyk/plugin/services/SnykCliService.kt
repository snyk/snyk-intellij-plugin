package io.snyk.plugin.services

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.*
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCliFile
import org.apache.log4j.Logger
import java.io.File

/**
 * Wrap work with Snyk CLI.
 */
@Service
class SnykCliService(val project: Project) {

    private var consoleCommandRunner: ConsoleCommandRunner? = null

    private val logger: Logger = Logger.getLogger(SnykCliService::class.java)

    fun isCliInstalled(): Boolean {
        logger.info("Check whether Snyk CLI is installed")

        return checkIsCliInstalledAutomaticallyByPlugin()
    }

    fun checkIsCliInstalledAutomaticallyByPlugin(): Boolean {
        logger.debug("Check whether Snyk CLI is installed by plugin automatically.")

        return getCliFile().exists()
    }

    fun scan(): CliResult {
        val commands = buildCliCommandsList(getApplicationSettingsStateService())

        val projectPath = project.basePath!!

        val snykResultJsonStr = getConsoleCommandRunner().execute(commands, projectPath)

        return if (snykResultJsonStr.contains("\"vulnerabilities\":") && !snykResultJsonStr.contains("\"error\":")) {
            jsonToCliResult(snykResultJsonStr)
        } else {
            val cliResult = CliResult()

            cliResult.error = jsonToCliError(snykResultJsonStr)

            cliResult
        }
    }

    fun jsonToCliResult(snykResultJsonStr: String): CliResult =
        Gson().fromJson(snykResultJsonStr, CliResult::class.java)

    fun jsonToCliError(snykResultJsonStr: String): CliError =
        Gson().fromJson(snykResultJsonStr, CliError::class.java)

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
        commands.add(getCliCommandPath())
        commands.add("--json")

        val customEndpoint = settings.customEndpointUrl

        if (customEndpoint != null && customEndpoint.isNotEmpty()) {
            commands.add("--api=$customEndpoint")
        }

        if (settings.ignoreUnknownCA) {
            commands.add("--insecure")
        }

        val organization = settings.organization

        if (organization != null && organization.isNotEmpty()) {
            commands.add("--org=$organization")
        }

        val additionalParameters = settings.getAdditionalParameters(project)

        if (additionalParameters != null && additionalParameters.isNotEmpty()) {
            commands.add(additionalParameters)
        }

        commands.add("test")

        logger.info("Cli parameters: $commands")

        return commands.toList()
    }

    fun setConsoleCommandRunner(newRunner: ConsoleCommandRunner?) {
        this.consoleCommandRunner = newRunner
    }

    private fun getCliCommandPath(): String {
        return when {
            checkIsCliInstalledAutomaticallyByPlugin() -> getCliFile().absolutePath
            else -> {
                throw RuntimeException("Snyk CLI not installed.")
            }
        }
    }

    private fun getConsoleCommandRunner(): ConsoleCommandRunner {
        if (consoleCommandRunner != null) {
            return consoleCommandRunner!!
        }

        return ConsoleCommandRunner()
    }

    fun isPackageJsonExists(): Boolean = File(project.basePath!!, "package.json").exists()
}
