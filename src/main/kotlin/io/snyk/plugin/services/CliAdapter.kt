package io.snyk.plugin.services

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.CliNotExistsException
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import org.jetbrains.annotations.TestOnly
import snyk.common.SnykError
import snyk.errorHandler.SentryErrorReporter

/**
 * Wrap work with Snyk CLI.
 * See [Kotlin's generic type's magic](https://kotlinlang.org/docs/generics.htm).
 */
abstract class CliAdapter<CliIssues, R : CliResult<CliIssues>>(val project: Project) {

    private var consoleCommandRunner = ConsoleCommandRunner()

    private val logger = logger<CliAdapter<CliIssues, R>>()

    private val projectPath: String = project.basePath
        ?: throw IllegalStateException("Scan should not be performed on Default project (with `null` project base dir)")

    /**
     * !!! Public for tests only !!!
     */
    fun execute(commands: List<String>): R =
        try {
            val cmds = buildCliCommandsList(commands)
            val apiToken = pluginSettings().token ?: ""
            val rawResultStr = consoleCommandRunner.execute(cmds, projectPath, apiToken, project)
            convertRawCliStringToCliResult(rawResultStr)
        } catch (exception: CliNotExistsException) {
            getErrorResult(exception.message ?: "Snyk CLI not installed.")
        }

    private fun getErrorResult(errorMsg: String): R {
        logger.warn(errorMsg)
        return getProductResult(null, listOf(SnykError(errorMsg, projectPath)))
    }

    protected abstract fun getProductResult(cliIssues: List<CliIssues>?, snykErrors: List<SnykError> = emptyList()): R

    /**
     *  `Gson().fromJson` could put `null` value into not-null field.
     *  That case should be handled(tested/checked) here and (specific) Exception thrown if needed.
     */
    @Throws(Exception::class)
    protected abstract fun sanitizeCliIssues(cliIssues: CliIssues): CliIssues

    /**
     * !!! Public for tests only !!!
     *
     * If result string not contains 'error' string and contain 'vulnerabilities' it says that everything is correct.
     * If result string not contains '{' it means CLI return an error.
     * And if result string contains 'error' and not contain 'vulnerabilities' it means CLI return error in JSON format.
     * if result == [ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER] - CLI scan process was terminated by user.
     * if result is Empty - CLI fail to run (some notification already done in CLI execution code).
     */
    fun convertRawCliStringToCliResult(rawStr: String): R =
        try {
            when {
                rawStr == ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER -> {
                    getProductResult(null)
                }
                rawStr.isEmpty() -> {
                    getErrorResult(CLI_PRODUCE_NO_OUTPUT)
                }
                rawStr.first() == '[' -> {
                    convertRawCliStringWithArrayToCliResult(rawStr)
                }
                rawStr.first() == '{' -> {
                    convertRawCliStringWithSingleEntryToCliResult(rawStr)
                }
                else -> getErrorResult(rawStr)
            }
        } catch (e: Throwable) {
            // we should catch all exceptions here including JsonParseException, JsonSyntaxException, etc.
            SentryErrorReporter.captureException(e)
            getErrorResult("Failed to parse CLI output: ${e.message ?: e.toString()}")
        }

    private fun convertRawCliStringWithSingleEntryToCliResult(rawStr: String): R = when {
        isSuccessCliJsonString(rawStr) -> {
            val cliIssues = Gson().fromJson(rawStr, getCliIIssuesClass())
            // `Gson().fromJson` could put `null` value into not-null field
            getSanitizedResultOrError {
                getProductResult(listOf(sanitizeCliIssues(cliIssues)))
            }
        }
        isErrorCliJsonString(rawStr) -> {
            val cliError = Gson().fromJson(rawStr, CliError::class.java)
            // `Gson().fromJson` could put `null` value into not-null field
            getSanitizedResultOrError {
                getProductResult(null, listOf(SnykError(cliError.message, cliError.path)))
            }
        }
        else -> getErrorResult(rawStr)
    }

    private fun getSanitizedResultOrError(resultProducer: () -> R): R =
        try {
            resultProducer()
        } catch (e: Exception) {
            SentryErrorReporter.captureException(e)
            getErrorResult("Failed to sanitize CLI output: ${e.message ?: e.toString()}")
        }

    private fun convertRawCliStringWithArrayToCliResult(rawStr: String): R {
        // see https://sites.google.com/site/gson/gson-user-guide#TOC-Serializing-and-Deserializing-Collection-with-Objects-of-Arbitrary-Types
        val jsonArray: JsonArray = JsonParser.parseString(rawStr).asJsonArray
        val allErrors: MutableList<SnykError> = mutableListOf()
        val cliIssues = jsonArray
            .map { it.toString() }
            .mapNotNull { elementAsSsting ->
                val cliResult = convertRawCliStringWithSingleEntryToCliResult(elementAsSsting)
                allErrors.addAll(cliResult.errors)
                cliResult.allCliIssues
            }
            .flatten()
        return getProductResult(cliIssues, allErrors)
    }

    // todo? potentially we should be able to get Class through `refined CliIssue` here, something like:
    // private inline fun <reified CliIssues> getCliIIssuesClass(): Class<CliIssues> = CliIssues::class.java
    // see https://kotlinlang.org/docs/inline-functions.htm#reified-type-parameters
    protected abstract fun getCliIIssuesClass(): Class<CliIssues>

    protected open fun isSuccessCliJsonString(jsonStr: String): Boolean = jsonStr.contains("\"vulnerabilities\":")

    private fun isErrorCliJsonString(jsonStr: String): Boolean = jsonStr.contains("\"error\":")

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

    companion object {
        const val CLI_PRODUCE_NO_OUTPUT = "CLI doesn't produce any output"
    }
}
