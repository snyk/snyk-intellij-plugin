package snyk.common.lsp

import com.intellij.openapi.project.Project
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import snyk.common.getEndpointUrl
import snyk.common.lsp.commands.ScanDoneEvent

class LSPClientWrapper(private val lsPath: String = getCliFile().absolutePath) {

    private lateinit var languageClient: LanguageClient

    /**
     * The launcher is used to start the language server as a separate process. and provides access to the LS
     */
    private lateinit var launcher: Launcher<LanguageServer>

    /**
     * Process is the started IO process
     */
    lateinit var process: Process

    fun initialize() {
        val cmd = listOf(lsPath, "language-server", "-l", "debug")

        val processBuilder = ProcessBuilder(cmd)
        process = processBuilder.start()
        languageClient = SnykLanguageClient()
        launcher = LSPLauncher.createClientLauncher(languageClient, process.inputStream, process.outputStream)
    }

    fun startListening() {
        // Start the server
        launcher.startListening()
    }

    fun sendInitializeMessage(project: Project? = null) {
        val params = InitializeParams()
        params.processId = ProcessHandle.current().pid().toInt()
        params.clientInfo = ClientInfo("IntelliJ IDEA Snyk Plugin")
        params.initializationOptions = getInitializationOptions(project)
        val initializeResult = launcher.remoteProxy.initialize(params).get()
        println(initializeResult)
    }

    fun sendReportAnalyticsCommand(scanDoneEvent: ScanDoneEvent) {
        val param = ExecuteCommandParams()
        param.command = "snyk.reportAnalytics"
        param.arguments = listOf(scanDoneEvent)
        launcher.remoteProxy.workspaceService.executeCommand(param)
    }

    private fun getInitializationOptions(project: Project?): Settings {
        val ps = pluginSettings()
        return Settings(
            activateSnykOpenSource = "false",
            activateSnykCode = "false",
            activateSnykIac = "false",
            insecure = ps.ignoreUnknownCA.toString(),
            endpoint = getEndpointUrl(),
            cliPath = getCliFile().absolutePath,
            token = ps.token,
        )
    }
}
