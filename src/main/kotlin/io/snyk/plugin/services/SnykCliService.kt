package io.snyk.plugin.services

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.*
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCliFile
import org.apache.log4j.Logger

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

    fun scan(): CliResult = try {
        val applicationSettings = getApplicationSettingsStateService()

        val commands = buildCliCommandsList(applicationSettings)
        val projectPath = project.basePath ?: ""
        val apiToken = applicationSettings.token ?: ""

        val rawResultStr = getConsoleCommandRunner().execute(commands, projectPath, apiToken)

        convertRawCliStringToCliResult(rawResultStr, projectPath)
    } catch (exception: CliNotExistsException) {
        CliResult(
            null,
            CliError(false, exception.message ?: "", project.basePath ?: ""))
    }

    /**
     * If result string not contains 'error' string and contain 'vulnerabilities' it says that everything is correct.
     * If result string not contains '{' it means CLI return an error.
     * And if result string contains 'error' and not contain 'vulnerabilities' it means CLI return error in JSON format.
     */
    fun convertRawCliStringToCliResult(rawStr: String, projectPath: String): CliResult =
        when {
            rawStr.first() == '[' -> {
                CliResult(Gson().fromJson(rawStr, Array<CliVulnerabilities>::class.java), null)
            }
            rawStr.first() == '{' -> {
                if (isSuccessCliJsonString(rawStr)) {
                    CliResult(arrayOf(Gson().fromJson(rawStr, CliVulnerabilities::class.java)), null)
                } else {
                    CliResult(null, Gson().fromJson(rawStr, CliError::class.java))
                }
            }
            else -> {
                CliResult(null, CliError(false, rawStr, projectPath))
            }
        }

    fun isSuccessCliJsonString(jsonStr: String): Boolean = jsonStr.contains("\"vulnerabilities\":")
        && !jsonStr.contains("\"error\":")

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

        if (additionalParameters == null || !additionalParameters.contains("--file")) {
            commands.add("--all-projects")
        }

        commands.add("test")

        logger.info("Cli parameters: $commands")

        return commands.toList()
    }

    fun setConsoleCommandRunner(newRunner: ConsoleCommandRunner?) {
        this.consoleCommandRunner = newRunner
    }

    private fun getCliCommandPath(): String = when {
        checkIsCliInstalledAutomaticallyByPlugin() -> getCliFile().absolutePath
        else -> {
            throw CliNotExistsException()
        }
    }

    private fun getConsoleCommandRunner(): ConsoleCommandRunner = consoleCommandRunner ?: ConsoleCommandRunner()
}
