package io.snyk.plugin.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import org.apache.log4j.Logger
import java.nio.charset.Charset

open class ConsoleCommandRunner {
    private val logger: Logger = Logger.getLogger(ConsoleCommandRunner::class.java)

    open fun execute(commands: List<String>, workDirectory: String = "/"): String {
        logger.info("Enter ConsoleCommandRunner.execute()")
        logger.info("Commands: $commands")

        val generalCommandLine = GeneralCommandLine(commands)

        generalCommandLine.charset = Charset.forName("UTF-8")
        generalCommandLine.setWorkDirectory(workDirectory)

        logger.info("GeneralCommandLine instance created.")
        logger.info("Execute ScriptRunnerUtil.getProcessOutput(...)")

        return ScriptRunnerUtil.getProcessOutput(generalCommandLine, ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER, 720000)
    }
}
