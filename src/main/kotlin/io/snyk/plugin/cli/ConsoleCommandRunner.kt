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
import com.intellij.util.net.HttpConfigurable
import io.snyk.plugin.controlExternalProcessWithProgressIndicator
import io.snyk.plugin.getWaitForResultsTimeout
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.common.getEndpointUrl
import snyk.errorHandler.SentryErrorReporter
import snyk.pluginInfo
import java.net.URLEncoder
import java.nio.charset.Charset

open class ConsoleCommandRunner {
    private val logger = logger<ConsoleCommandRunner>()

    open fun execute(
        commands: List<String>,
        workDirectory: String? = null,
        apiToken: String = "",
        project: Project,
        outputConsumer: (line: String) -> Unit = {}
    ): String {
        logger.info("Call to execute commands: $commands")

        val generalCommandLine = GeneralCommandLine(commands)

        generalCommandLine.charset = Charset.forName("UTF-8")
        workDirectory?.let { generalCommandLine.setWorkDirectory(it) }

        setupCliEnvironmentVariables(generalCommandLine, apiToken)

        val processHandler = try {
            OSProcessHandler(generalCommandLine)
        } catch (e: ExecutionException) {
            //  if CLI is still downloading (or temporarily blocked by Antivirus) we'll get ProcessNotCreatedException
            val message = "Not able to run CLI, try again later."
            SnykBalloonNotificationHelper.showWarn(message, project)
            logger.warn(message, e)
            return ""
        }
        val parentIndicator = ProgressManager.getInstance().progressIndicator

        var firstLine = true
        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (firstLine) {
                    logger.debug("Executing:\n${event.text}")
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
                processHandler, ScriptRunnerUtil.STDOUT_OR_STDERR_OUTPUT_KEY_FILTER, timeout
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
    fun setupCliEnvironmentVariables(commandLine: GeneralCommandLine, apiToken: String) {
        if (apiToken.isNotEmpty()) {
            commandLine.environment["SNYK_TOKEN"] = apiToken
        }
        commandLine.environment["SNYK_API"] = getEndpointUrl()

        if (!pluginSettings().usageAnalyticsEnabled) {
            commandLine.environment["SNYK_CFG_DISABLE_ANALYTICS"] = "1"
        }

        commandLine.environment["SNYK_INTEGRATION_NAME"] = pluginInfo.integrationName
        commandLine.environment["SNYK_INTEGRATION_VERSION"] = pluginInfo.integrationVersion
        commandLine.environment["SNYK_INTEGRATION_ENVIRONMENT"] = pluginInfo.integrationEnvironment
        commandLine.environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"] = pluginInfo.integrationEnvironmentVersion
        val proxySettings = HttpConfigurable.getInstance()
        val proxyHost = proxySettings.PROXY_HOST
        if (proxySettings != null && proxySettings.USE_HTTP_PROXY && proxyHost.isNotEmpty()) {
            val authentication = if (proxySettings.PROXY_AUTHENTICATION) {
                val auth = proxySettings.getPromptedAuthentication(proxyHost, "Snyk: Please enter your proxy password")
                if (auth == null) "" else auth.userName.urlEncode() + ":" + String(auth.password).urlEncode() + "@"
            } else ""
            commandLine.environment["http_proxy"] = "http://$authentication$proxyHost:${proxySettings.PROXY_PORT}"
            commandLine.environment["https_proxy"] = "http://$authentication$proxyHost:${proxySettings.PROXY_PORT}"
        }
    }

    private fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")

    companion object {
        const val PROCESS_CANCELLED_BY_USER = "PROCESS_CANCELLED_BY_USER"
    }
}
