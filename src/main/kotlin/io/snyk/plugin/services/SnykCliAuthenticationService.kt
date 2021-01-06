package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getPluginPath

@Service
class SnykCliAuthenticationService {
    private val logger = logger<SnykCliAuthenticationService>()

    private var consoleCommandRunner: ConsoleCommandRunner? = null
    private var token: String = ""

    fun authenticate(): String {
        downloadCliIfNeeded()
        executeAuthCommand()
        executeGetConfigApiCommand()

        return token
    }

    private fun downloadCliIfNeeded() {
        val downloadCliTask: () -> Unit = {
            if (!getCliFile().exists()) {
                val downloaderService = service<SnykCliDownloaderService>()
                downloaderService.downloadLatestRelease(ProgressManager.getInstance().progressIndicator)
            } else {
                logger.info("Skip CLI download, since it was already downloaded")
            }
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            downloadCliTask, "Download CLI latest release", true, null
        )
    }

    private fun executeAuthCommand() {
        val authTask: () -> Unit = {
            val settings = service<SnykApplicationSettingsStateService>()
            val commands = buildCliCommands(listOf("auth"), settings)
            getConsoleCommandRunner().execute(commands, getPluginPath(), "")
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            authTask, "Authenticate CLI...", true, null
        )
    }

    private fun executeGetConfigApiCommand() {
        val getConfigApiTask: () -> Unit = {
            val settings = service<SnykApplicationSettingsStateService>()
            val commands = buildCliCommands(listOf("config", "get", "api"), settings)
            val getConfigApiOutput = getConsoleCommandRunner().execute(commands, getPluginPath(), "")
            token = getConfigApiOutput.replace("\n", "").replace("\r", "")
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            getConfigApiTask, "Get API token", true, null
        )
    }

    private fun buildCliCommands(commands: List<String>, settings: SnykApplicationSettingsStateService): List<String> {
        //TODO: handle custom api endpoint from settings
        val cli: MutableList<String> = mutableListOf()
        cli.add(getCliFile().absolutePath)
        cli.addAll(commands)

        return cli.toList()
    }

    private fun getConsoleCommandRunner(): ConsoleCommandRunner {
        if (consoleCommandRunner != null) {
            return consoleCommandRunner!!
        }
        return ConsoleCommandRunner()
    }
}
