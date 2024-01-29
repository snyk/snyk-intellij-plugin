package snyk.common.lsp

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import snyk.common.EnvironmentHelper
import snyk.common.getEndpointUrl
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService

class LanguageServerWrapper(private val lsPath: String = getCliFile().absolutePath, private val project: Project) {
    private val gson = com.google.gson.Gson()

    /**
     * The language client is used to receive messages from LS
     */
    lateinit var languageClient: SnykLanguageClient

    /**
     * The language server allows access to the actual LS implementation
     */
    lateinit var languageServer: LanguageServer

    /**
     * The launcher is used to start the language server as a separate process. and provides access to the LS
     */
    private lateinit var launcher: Launcher<LanguageServer>

    /**
     * Process is the started IO process
     */
    lateinit var process: Process

    fun initialize() {
        val snykLanguageClient = SnykLanguageClient(project)
        languageClient = snykLanguageClient
        val logLevel = if (snykLanguageClient.logger.isDebugEnabled) "debug" else "info"
        val cmd = listOf(lsPath, "language-server", "-l", logLevel, "-f", "/tmp/snyk-ls-intellij.log")

        val processBuilder = ProcessBuilder(cmd)
        pluginSettings().token?.let { EnvironmentHelper.updateEnvironment(processBuilder.environment(), it) }

        process = processBuilder.start()
        launcher = LSPLauncher.createClientLauncher(languageClient, process.inputStream, process.outputStream)
        languageServer = launcher.remoteProxy
    }

    fun startListening() {
        // Start the server
        launcher.startListening()
    }

    fun sendInitializeMessage(project: Project) {
        val workspaceFolders = mutableListOf<WorkspaceFolder>()
        ProjectRootManager.getInstance(project)
            .contentRoots
            .mapNotNull { WorkspaceFolder(it.url, it.name) }.toCollection(workspaceFolders)

        val params = InitializeParams()
        params.processId = ProcessHandle.current().pid().toInt()
        params.clientInfo = ClientInfo("${pluginInfo.integrationName}/lsp4j")
        params.initializationOptions = getInitializationOptions()
        params.workspaceFolders = workspaceFolders

        languageServer.initialize(params).get()
    }

    fun sendReportAnalyticsCommand(scanDoneEvent: ScanDoneEvent) {
        try {
            val eventString = gson.toJson(scanDoneEvent)
            val param = ExecuteCommandParams()
            param.command = "snyk.reportAnalytics"
            param.arguments = listOf(eventString)
            languageServer.workspaceService.executeCommand(param)
        } catch (ignored: Exception) {
            // do nothing to not break UX for analytics
        }
    }

    fun sendScanCommand() {
        try {
            val param = ExecuteCommandParams()
            param.command = "snyk.workspace.scan"
            languageServer.workspaceService.executeCommand(param)
        } catch (ignored: Exception) {
            // do nothing to not break UX for analytics
        }
    }

    fun getInitializationOptions(): LanguageServerSettings {
        val ps = pluginSettings()
        return LanguageServerSettings(
            activateSnykOpenSource = ps.ossScanEnable.toString(),
            activateSnykCodeSecurity = ps.snykCodeSecurityIssuesScanEnable.toString(),
            activateSnykCodeQuality = ps.snykCodeQualityIssuesScanEnable.toString(),
            activateSnykIac = ps.iacScanEnabled.toString(),
            organization = ps.organization,
            insecure = ps.ignoreUnknownCA.toString(),
            endpoint = getEndpointUrl(),
            cliPath = getCliFile().absolutePath,
            token = ps.token,
            filterSeverity = SeverityFilter(
                critical = ps.criticalSeverityEnabled,
                high = ps.highSeverityEnabled,
                medium = ps.mediumSeverityEnabled,
                low = ps.lowSeverityEnabled
            ),
            trustedFolders = service<WorkspaceTrustService>().settings.getTrustedPaths(),
            scanningMode = "auto"
        )
    }
}
