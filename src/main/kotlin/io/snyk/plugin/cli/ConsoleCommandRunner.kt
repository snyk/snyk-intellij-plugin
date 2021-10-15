package io.snyk.plugin.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.snyk.plugin.controlExternalProcessWithProgressIndicator
import io.snyk.plugin.getWaitForResultsTimeout
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.errorHandler.SentryErrorReporter
import snyk.pluginInfo
import java.nio.charset.Charset

open class ConsoleCommandRunner {
    private val logger = logger<ConsoleCommandRunner>()

    open fun execute(
        commands: List<String>,
        workDirectory: String = "/",
        apiToken: String = "",
        project: Project,
        outputConsumer: (line: String) -> Unit = {}
    ): String {
        logger.info("Call to execute commands: $commands")

        val generalCommandLine = GeneralCommandLine(commands)

        generalCommandLine.charset = Charset.forName("UTF-8")
        generalCommandLine.setWorkDirectory(workDirectory)

        setupCliEnvironmentVariables(generalCommandLine, apiToken)

        val processHandler = try {
            OSProcessHandler(generalCommandLine)
        } catch (e: ExecutionException) {
            //  if CLI is still downloading (or temporarily blocked by Antivirus) we'll get ProcessNotCreatedException
            SnykBalloonNotificationHelper.showWarn("Not able to run CLI, try again later.", project)
            return ""
        }
        val parentIndicator = ProgressManager.getInstance().progressIndicator

        var firstLine = true
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (firstLine) {
                    logger.debug("Executing: \"${event.text}\"")
                    firstLine = false
                }
                outputConsumer(event.text)
            }
        })

        var wasProcessTerminated = false
        controlExternalProcessWithProgressIndicator(parentIndicator) {
            if (!processHandler.isProcessTerminated) {
                ScriptRunnerUtil.terminateProcessHandler(processHandler, 50, null)
                wasProcessTerminated = true
            }
        }

        logger.debug("Execute ScriptRunnerUtil.getProcessOutput(...)")
        val timeout = getWaitForResultsTimeout()
        val processOutput = try {
            ScriptRunnerUtil.getProcessOutput(
                processHandler,
                ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER,
                timeout
            )
        } catch (e: ExecutionException) {
            SentryErrorReporter.captureException(e)
            "Execution timeout [${timeout / 1000} sec] is reached with NO results produced"
        }

        return if (wasProcessTerminated) PROCESS_CANCELLED_BY_USER else processOutput
    }

    /**
     * Setup environment variables for CLI.
     */
    fun setupCliEnvironmentVariables(generalCommandLine: GeneralCommandLine, apiToken: String) {
        if (apiToken.isNotEmpty()) {
            generalCommandLine.environment["SNYK_TOKEN"] = apiToken
        }

        generalCommandLine.environment["SNYK_INTEGRATION_NAME"] = pluginInfo.integrationName
        generalCommandLine.environment["SNYK_INTEGRATION_VERSION"] = pluginInfo.integrationVersion
        generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT"] = pluginInfo.integrationEnvironment
        generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"] = pluginInfo.integrationEnvironmentVersion
    }

    companion object {
        const val PROCESS_CANCELLED_BY_USER = "PROCESS_CANCELLED_BY_USER"
    }
}
