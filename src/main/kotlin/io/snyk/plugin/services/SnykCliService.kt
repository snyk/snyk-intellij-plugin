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
        try {
            val applicationSettings = getApplicationSettingsStateService()

            val commands = buildCliCommandsList(applicationSettings)

            val projectPath = project.basePath!!

            val apiToken = if (applicationSettings.token != null) {
                applicationSettings.token!!
            } else {
                ""
            }

            val rawResultStr = getConsoleCommandRunner().execute(commands, projectPath, apiToken)

            return convertRawCliStringToCliResult(rawResultStr, projectPath)
        } catch (exception: CliNotExistsException) {
            return CliResult(
                null,
                CliError(false, exception.message ?: "", project.basePath ?: ""))
        }
    }

    /**
     * If result string not contains 'error' string and contain 'vulnerabilities' it says that everything is correct.
     * If result string not contains '{' it means CLI return an error.
     * And if result string contains 'error' and not contain 'vulnerabilities' it means CLI return error in JSON format.
     * if result string is _empty_ - CLI scan process was intentionally terminated by user..
     */
    fun convertRawCliStringToCliResult(rawStr: String, projectPath: String): CliResult =
        when {
            rawStr.isEmpty() -> CliResult(null, null)
            rawStr.first() == '[' -> {
                CliResult(Gson().fromJson(rawStr, Array<CliVulnerabilitiesForFile>::class.java), null)
            }
            rawStr.first() == '{' -> {
                if (isSuccessCliJsonString(rawStr)) {
                    CliResult(arrayOf(Gson().fromJson(rawStr, CliVulnerabilitiesForFile::class.java)), null)
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
        commands.add("test")
        commands.add("--json")

        val customEndpoint = settings.customEndpointUrl
        if (customEndpoint != null && customEndpoint.isNotEmpty()) {
            commands.add("--API=$customEndpoint")
        }

        if (settings.ignoreUnknownCA) {
            commands.add("--insecure")
        }

        val organization = settings.organization
        if (organization != null && organization.isNotEmpty()) {
            commands.add("--org=$organization")
        }

        if (!settings.usageAnalyticsEnabled) {
            commands.add("--DISABLE_ANALYTICS")
        }

        val additionalParameters = settings.getAdditionalParameters(project)

        if (additionalParameters != null && additionalParameters.trim().isNotEmpty()) {
            commands.addAll(additionalParameters.trim().split(" "))
        }

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
                throw CliNotExistsException()
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
