package io.snyk.plugin.cli

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import io.snyk.plugin.getCliFile
import org.apache.log4j.Logger
import org.jetbrains.annotations.CalledInAwt
import java.nio.charset.Charset
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
        val projectPath = project.basePath

        val commands = mutableListOf<String>()

        commands.add("snyk")
        commands.add("--json")
        commands.add("test")

        val generalCommandLine = GeneralCommandLine(commands)

        generalCommandLine.charset = Charset.forName("UTF-8")
        generalCommandLine.setWorkDirectory(projectPath)

        val snykResultJsonStr = ScriptRunnerUtil
            .getProcessOutput(generalCommandLine, ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER, 720000)

        return Gson().fromJson(snykResultJsonStr, CliResult::class.java)
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
}
