package snyk.common.lsp

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.RunUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import snyk.common.EnvironmentHelper
import snyk.common.getEndpointUrl
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.pluginInfo
import java.util.concurrent.TimeUnit

private const val DEFAULT_SLEEP_TIME = 100L
private const val INITIALIZATION_TIMEOUT = 20L

@Suppress("TooGenericExceptionCaught")
class LanguageServerWrapper(private val lsPath: String = getCliFile().absolutePath) {
    private val gson = com.google.gson.Gson()
    val logger = Logger.getInstance("Snyk Language Server")

    /**
     * The language client is used to receive messages from LS
     */
    @Suppress("MemberVisibilityCanBePrivate")
    lateinit var languageClient: LanguageClient

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

    var isInitializing: Boolean = false
    val isInitialized: Boolean
        get() = ::languageClient.isInitialized && ::languageServer.isInitialized &&
            ::process.isInitialized && process.info().startInstant().isPresent

    @OptIn(DelicateCoroutinesApi::class)
    internal fun initialize() {
        try {
            isInitializing = true
            val snykLanguageClient = SnykLanguageClient()
            languageClient = snykLanguageClient
            val logLevel = if (snykLanguageClient.logger.isDebugEnabled) "debug" else "info"
            val cmd = listOf(lsPath, "language-server", "-l", logLevel)

            val processBuilder = ProcessBuilder(cmd)
            pluginSettings().token?.let { EnvironmentHelper.updateEnvironment(processBuilder.environment(), it) }

            process = processBuilder.start()
            launcher = LSPLauncher.createClientLauncher(languageClient, process.inputStream, process.outputStream)
            languageServer = launcher.remoteProxy

            GlobalScope.launch {
                process.errorStream.bufferedReader().forEachLine { println(it) }
            }

            launcher.startListening()

            sendInitializeMessage()
        } catch (e: Exception) {
            logger.error(e)
        } finally {
            isInitializing = false
        }
    }

    private fun determineWorkspaceFolders(): List<WorkspaceFolder> {
        val workspaceFolders = mutableListOf<WorkspaceFolder>()
        ProjectManager.getInstance().openProjects.forEach { project ->
            ProjectRootManager.getInstance(project)
                .contentRoots
                .mapNotNull { WorkspaceFolder(it.url, it.name) }.toCollection(workspaceFolders)
        }
        return workspaceFolders.toList()
    }

    fun sendInitializeMessage() {
        val workspaceFolders = determineWorkspaceFolders()

        val params = InitializeParams()
        params.processId = ProcessHandle.current().pid().toInt()
        params.clientInfo = ClientInfo("${pluginInfo.integrationName}/lsp4j")
        params.initializationOptions = getInitializationOptions()
        params.workspaceFolders = workspaceFolders

        languageServer.initialize(params).get(INITIALIZATION_TIMEOUT, TimeUnit.SECONDS)
    }

    fun updateWorkspaceFolders(project: Project, added: List<WorkspaceFolder>, removed: List<WorkspaceFolder>) {
        RunUtils.instance.runInBackground(
            project,
            "Updating workspace folders for project: ${project.name}"
        ) {
            try {
                ensureLanguageServerInitialized()
                val params = DidChangeWorkspaceFoldersParams()
                params.event = WorkspaceFoldersChangeEvent(added, removed)
                languageServer.workspaceService.didChangeWorkspaceFolders(params)
            } catch (e: Exception) {
                logger.error(e)
            }
        }
    }

    private fun ensureLanguageServerInitialized() {
        while (isInitializing) {
            Thread.sleep(DEFAULT_SLEEP_TIME)
        }
        if (!isInitialized) {
            initialize()
        }
    }

    fun sendReportAnalyticsCommand(scanDoneEvent: ScanDoneEvent) {
        ensureLanguageServerInitialized()
        try {
            val eventString = gson.toJson(scanDoneEvent)
            val param = ExecuteCommandParams()
            param.command = "snyk.reportAnalytics"
            param.arguments = listOf(eventString)
            languageServer.workspaceService.executeCommand(param)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun getInitializationOptions(): LanguageServerSettings {
        val ps = pluginSettings()
        return LanguageServerSettings(
            activateSnykOpenSource = "false",
            activateSnykCode = "false",
            activateSnykIac = "false",
            insecure = ps.ignoreUnknownCA.toString(),
            endpoint = getEndpointUrl(),
            cliPath = getCliFile().absolutePath,
            token = ps.token,
            enableTrustedFoldersFeature = "false",
            filterSeverity = SeverityFilter(
                critical = ps.criticalSeverityEnabled,
                high = ps.highSeverityEnabled,
                medium = ps.mediumSeverityEnabled,
                low = ps.lowSeverityEnabled
            ),
        )
    }
}
