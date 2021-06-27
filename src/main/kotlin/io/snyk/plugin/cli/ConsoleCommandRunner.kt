package io.snyk.plugin.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import io.snyk.plugin.SnykPostStartupActivity
import io.snyk.plugin.controlExternalProcessWithProgressIndicator
import org.apache.log4j.Logger
import java.nio.charset.Charset

open class ConsoleCommandRunner {
    private val logger: Logger = Logger.getLogger(ConsoleCommandRunner::class.java)

    private val snykPluginVersion: String by lazy {
        val featureTrainerPluginId = PluginManagerCore.getPluginByClassName(SnykPostStartupActivity::class.java.name)
        PluginManagerCore.getPlugin(featureTrainerPluginId)?.version ?: "UNKNOWN"
    }

    open fun execute(
        commands: List<String>,
        workDirectory: String = "/",
        apiToken: String = "",
        outputConsumer: (line: String) -> Unit = {}
    ): String {
        logger.info("Enter ConsoleCommandRunner.execute()")
        logger.info("Commands: $commands")

        val generalCommandLine = GeneralCommandLine(commands)

        generalCommandLine.charset = Charset.forName("UTF-8")
        generalCommandLine.setWorkDirectory(workDirectory)

        setupCliEnvironmentVariables(generalCommandLine, apiToken)

        logger.info("GeneralCommandLine instance created.")

        val processHandler = OSProcessHandler(generalCommandLine)
        val parentIndicator = ProgressManager.getInstance().progressIndicator

        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) = outputConsumer(event.text)
        })

        var wasProcessTerminated = false
        controlExternalProcessWithProgressIndicator(parentIndicator) {
            if (!processHandler.isProcessTerminated) {
                ScriptRunnerUtil.terminateProcessHandler(processHandler, 50, null)
                wasProcessTerminated = true
            }
        }

        logger.info("Execute ScriptRunnerUtil.getProcessOutput(...)")
        val processOutput = ScriptRunnerUtil.getProcessOutput(processHandler, ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER, 720000)

        return if (wasProcessTerminated) PROCESS_CANCELLED_BY_USER else processOutput
    }

    /**
     * Setup environment variables for CLI.
     */
    fun setupCliEnvironmentVariables(generalCommandLine: GeneralCommandLine, apiToken: String) {
        if (apiToken.isNotEmpty()) {
            generalCommandLine.environment["SNYK_TOKEN"] = apiToken
        }

        generalCommandLine.environment["SNYK_INTEGRATION_NAME"] = "JETBRAINS_IDE"
        generalCommandLine.environment["SNYK_INTEGRATION_VERSION"] = snykPluginVersion

        val applicationInfo = ApplicationInfo.getInstance()

        val versionName = when (val name = applicationInfo.versionName) {
            "IntelliJ IDEA", "PyCharm" -> "$name ${applicationInfo.apiVersion.substring(0, 2)}"
            else -> name
        }

        generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT"] = versionName.toUpperCase()
        generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"] = applicationInfo.fullVersion
    }

    companion object {
        const val PROCESS_CANCELLED_BY_USER = "PROCESS_CANCELLED_BY_USER"
    }
}
