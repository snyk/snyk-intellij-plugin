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

    private var isAuthenticated = false
    private var token: String = ""

    fun authenticate(): String {
        downloadCliIfNeeded()
        if (getCliFile().exists()) executeAuthCommand()
        if (isAuthenticated) executeGetConfigApiCommand()

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
            downloadCliTask, "Download Snyk CLI latest release", true, null
        )
    }

    private fun executeAuthCommand() {
        val authTask: () -> Unit = {
            val commands = buildCliCommands(listOf("auth"))
            val output = getConsoleCommandRunner().execute(commands, getPluginPath(), "")
            isAuthenticated = output.contains("Your account has been authenticated.")
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            authTask, "Authenticating Snyk plugin...", true, null
        )
    }

    private fun executeGetConfigApiCommand() {
        val getConfigApiTask: () -> Unit = {
            val commands = buildCliCommands(listOf("config", "get", "api"))
            val getConfigApiOutput = getConsoleCommandRunner().execute(commands, getPluginPath(), "")
            token = getConfigApiOutput.replace("\n", "").replace("\r", "")
        }
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            getConfigApiTask, "Get Snyk API token", true, null
        )
    }

    private fun buildCliCommands(commands: List<String>): List<String> {
        val settings = service<SnykApplicationSettingsStateService>()
        val cli: MutableList<String> = mutableListOf(getCliFile().absolutePath)
        cli.addAll(commands)

        val customEndpoint = settings.customEndpointUrl
        if (customEndpoint != null && customEndpoint.isNotEmpty()) {
            cli.add("--API=$customEndpoint")
        }

        if (settings.ignoreUnknownCA) {
            cli.add("--insecure")
        }

        return cli.toList()
    }

    private fun getConsoleCommandRunner(): ConsoleCommandRunner {
        return ConsoleCommandRunner()
    }
}
