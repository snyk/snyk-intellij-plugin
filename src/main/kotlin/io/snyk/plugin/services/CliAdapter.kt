package io.snyk.plugin.services

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliNotExistsException
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import org.jetbrains.annotations.TestOnly

/**
 * Wrap work with Snyk CLI.
 */
abstract class CliAdapter<R>(val project: Project) {

    private var consoleCommandRunner = ConsoleCommandRunner()

    private val logger = logger<CliAdapter<R>>()

    protected val projectPath: String = project.basePath
        ?: throw IllegalStateException("Scan should not be performed on Default project (with `null` project base dir)")

    fun execute(commands: List<String>): R =
        try {
            val cmds = buildCliCommandsList(commands)
            val apiToken = pluginSettings().token ?: ""
            val rawResultStr = consoleCommandRunner.execute(cmds, projectPath, apiToken, project)
            convertRawCliStringToCliResult(rawResultStr)
        } catch (exception: CliNotExistsException) {
            getErrorResult(exception.message ?: "Snyk CLI not installed.")
        }

    protected abstract fun getErrorResult(errorMsg: String): R

    /**
     * if rawStr == [ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER] - CLI scan process
     * was intentionally terminated by user.
     */
    abstract fun convertRawCliStringToCliResult(rawStr: String): R

    /**
     * Build list of commands for run Snyk CLI command.
     * @return List<String>
     */
    private fun buildCliCommandsList(cmds: List<String>): List<String> {
        logger.debug("Enter buildCliCommandsList")
        val settings = pluginSettings()

        val commands: MutableList<String> = mutableListOf()
        commands.add(getCliCommandPath())
        commands.addAll(cmds)

        if (settings.ignoreUnknownCA) {
            commands.add("--insecure")
        }

        val organization = settings.organization
        if (organization != null && organization.isNotEmpty()) {
            commands.add("--org=$organization")
        }

        commands.addAll(buildExtraOptions())

        logger.debug("Cli parameters: $commands")

        return commands.toList()
    }

    @Suppress("FunctionName")
    @TestOnly
    fun buildCliCommandsList_TEST_ONLY(cmds: List<String>): List<String> = buildCliCommandsList(cmds)

    abstract fun buildExtraOptions(): List<String>

    @TestOnly
    fun setConsoleCommandRunner(newRunner: ConsoleCommandRunner) {
        this.consoleCommandRunner = newRunner
    }

    private fun getCliCommandPath(): String =
        if (isCliInstalled()) getCliFile().absolutePath else throw CliNotExistsException()
}
