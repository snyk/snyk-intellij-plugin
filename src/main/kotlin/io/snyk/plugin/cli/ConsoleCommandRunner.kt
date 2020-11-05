package io.snyk.plugin.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import io.snyk.plugin.SnykPostStartupActivity
import org.apache.log4j.Logger
import java.nio.charset.Charset

open class ConsoleCommandRunner {
    private val logger: Logger = Logger.getLogger(ConsoleCommandRunner::class.java)

    private val snykPluginVersion: String by lazy {
        val featureTrainerPluginId = PluginManagerCore.getPluginByClassName(SnykPostStartupActivity::class.java.name)
        PluginManagerCore.getPlugin(featureTrainerPluginId)?.version ?: "UNKNOWN"
    }

    open fun execute(commands: List<String>, workDirectory: String = "/", apiToken: String = ""): String {
        logger.info("Enter ConsoleCommandRunner.execute()")
        logger.info("Commands: $commands")

        val generalCommandLine = GeneralCommandLine(commands)

        generalCommandLine.charset = Charset.forName("UTF-8")
        generalCommandLine.setWorkDirectory(workDirectory)

        if (apiToken.isNotEmpty()) {
            generalCommandLine.environment["SNYK_TOKEN"] = apiToken
        }

        generalCommandLine.environment["SNYK_INTEGRATION_NAME"] = "JETBRAINS_IDE"
        generalCommandLine.environment["SNYK_INTEGRATION_VERSION"] = snykPluginVersion

        val applicationInfo = ApplicationInfo.getInstance()

        generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT"] = applicationInfo.versionName.toUpperCase()
        generalCommandLine.environment["SNYK_INTEGRATION_ENVIRONMENT_VERSION"] = applicationInfo.fullVersion

        logger.info("GeneralCommandLine instance created.")
        logger.info("Execute ScriptRunnerUtil.getProcessOutput(...)")

        return ScriptRunnerUtil.getProcessOutput(generalCommandLine, ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER, 720000)
    }
}
