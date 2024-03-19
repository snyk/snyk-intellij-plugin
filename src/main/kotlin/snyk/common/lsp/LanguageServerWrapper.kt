package snyk.common.lsp

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.toNioPathOrNull
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.getUserAgentString
import io.snyk.plugin.isSnykCodeLSEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeLensCapabilities
import org.eclipse.lsp4j.CodeLensWorkspaceCapabilities
import org.eclipse.lsp4j.DiagnosticCapabilities
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlineValueWorkspaceCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.eclipse.lsp4j.WorkspaceEditCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceFoldersChangeEvent
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.json.JSONObject
import snyk.common.EnvironmentHelper
import snyk.common.getEndpointUrl
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

private const val DEFAULT_SLEEP_TIME = 100L
private const val INITIALIZATION_TIMEOUT = 20L

@Suppress("TooGenericExceptionCaught")
class LanguageServerWrapper(
    private val lsPath: String = getCliFile().absolutePath,
    private val executorService: ExecutorService = Executors.newCachedThreadPool(),
) {
    private val gson = com.google.gson.Gson()
    val logger = Logger.getInstance("Snyk Language Server")

    /**
     * The language client is used to receive messages from LS
     */
    @Suppress("MemberVisibilityCanBePrivate")
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

    private var isInitializing: Boolean = false

    val isInitialized: Boolean
        get() = ::languageClient.isInitialized &&
            ::languageServer.isInitialized &&
            ::process.isInitialized &&
            process.info().startInstant().isPresent &&
            process.isAlive

    @OptIn(DelicateCoroutinesApi::class)
    internal fun initialize() {
        if (lsPath.toNioPathOrNull()?.exists() == false) {
            val message = "Snyk Language Server not found. Please make sure the Snyk CLI is installed at $lsPath."
            SnykBalloonNotificationHelper.showError(message, null)
            return
        }
        try {
            isInitializing = true
            val snykLanguageClient = SnykLanguageClient()
            languageClient = snykLanguageClient
            val logLevel = if (snykLanguageClient.logger.isDebugEnabled) "debug" else "info"
            val cmd = listOf(lsPath, "language-server", "-l", logLevel, "-f", "/tmp/snyk-ls.log")

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
            logger.warn(e)
        } finally {
            isInitializing = false
        }
    }

    fun shutdown(): Future<*> {
        return executorService.submit {
            process.destroyForcibly()
        }
    }

    private fun determineWorkspaceFolders(): List<WorkspaceFolder> {
        val workspaceFolders = mutableSetOf<WorkspaceFolder>()
        ProjectManager.getInstance().openProjects.forEach { project ->
            workspaceFolders.addAll(getWorkspaceFolders(project))
        }
        return workspaceFolders.toList()
    }

    fun getWorkspaceFolders(project: Project) =
        project.getContentRootVirtualFiles().mapNotNull { WorkspaceFolder(it.url, it.name) }.toSet()

    fun sendInitializeMessage() {
        val workspaceFolders = determineWorkspaceFolders()

        val params = InitializeParams()
        params.processId = ProcessHandle.current().pid().toInt()
        val clientInfo = getUserAgentString()
        params.clientInfo = ClientInfo(clientInfo, "lsp4j")
        params.initializationOptions = getSettings()
        params.workspaceFolders = workspaceFolders
        params.capabilities = getCapabilities()

        languageServer.initialize(params).get(INITIALIZATION_TIMEOUT, TimeUnit.SECONDS)
        languageServer.initialized(InitializedParams())
    }

    private fun getCapabilities(): ClientCapabilities =
        ClientCapabilities().let { clientCapabilities ->
            clientCapabilities.workspace = WorkspaceClientCapabilities().let { workspaceClientCapabilities ->
                workspaceClientCapabilities.workspaceFolders = true
                workspaceClientCapabilities.workspaceEdit =
                    WorkspaceEditCapabilities().let { workspaceEditCapabilities ->
                        workspaceEditCapabilities.documentChanges = true
                        workspaceEditCapabilities
                    }
                workspaceClientCapabilities.codeLens = CodeLensWorkspaceCapabilities(true)
                workspaceClientCapabilities.inlineValue = InlineValueWorkspaceCapabilities(true)
                workspaceClientCapabilities.applyEdit = true
                workspaceClientCapabilities
            }
            clientCapabilities.textDocument = TextDocumentClientCapabilities().let {
                it.codeLens = CodeLensCapabilities(true)
                it.codeAction = CodeActionCapabilities(true)
                it.diagnostic = DiagnosticCapabilities(true)

                it
            }
            return clientCapabilities
        }

    fun updateWorkspaceFolders(added: Set<WorkspaceFolder>, removed: Set<WorkspaceFolder>) {
        try {
            ensureLanguageServerInitialized()
            val params = DidChangeWorkspaceFoldersParams()
            params.event = WorkspaceFoldersChangeEvent(added.toList(), removed.toList())
            languageServer.workspaceService.didChangeWorkspaceFolders(params)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun ensureLanguageServerInitialized() {
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

    fun sendScanCommand() {
        ensureLanguageServerInitialized()
        val project = ProjectUtil.getActiveProject()
        if (project == null) {
            logger.warn("No active project found, not sending scan command.")
            return
        }
        project.getContentRootVirtualFiles().filterNotNull().toSet().forEach {
            val trustService = service<WorkspaceTrustService>()
            if (trustService.isPathTrusted(it.toNioPath())) {
                sendFolderScanCommand(it.path)
            }
        }
    }

    fun sendFeatureFlagCommand(featureFlag: String): Boolean {
        ensureLanguageServerInitialized()
        try {
            val param = ExecuteCommandParams()
            param.command = "snyk.getFeatureFlagStatus"
            param.arguments = listOf(featureFlag)
            val result = languageServer.workspaceService.executeCommand(param).get()

            val resultJson = JSONObject(result.toString())
            val ok = resultJson.getBoolean("ok")
            val userMessage = resultJson.optString("userMessage")

            if (ok) {
                logger.info("Feature flag $featureFlag is enabled.")
                return true
            } else {
                logger.warn("Feature flag $featureFlag is disabled. Message: $userMessage")
                return false
            }

        } catch (e: Exception) {
            logger.error("Error while checking feature flag: ${e.message}", e)
            return false
        }
    }

    private fun sendFolderScanCommand(folder: String) {
        try {
            val param = ExecuteCommandParams()
            param.command = "snyk.workspaceFolder.scan"
            param.arguments = listOf(folder)
            languageServer.workspaceService.executeCommand(param)
        } catch (ignored: Exception) {
            // do nothing to not break UX for analytics
        }
    }

    fun getSettings(): LanguageServerSettings {
        val ps = pluginSettings()
        return LanguageServerSettings(
            activateSnykOpenSource = false.toString(),
            activateSnykCodeSecurity = (isSnykCodeLSEnabled() && ps.snykCodeSecurityIssuesScanEnable).toString(),
            activateSnykCodeQuality = (isSnykCodeLSEnabled() && ps.snykCodeQualityIssuesScanEnable).toString(),
            activateSnykIac = false.toString(),
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
            enableTrustedFoldersFeature = "false",
            scanningMode = if (!ps.scanOnSave) "manual" else "auto",
            integrationName = pluginInfo.integrationName,
            integrationVersion = pluginInfo.integrationVersion,
        )
    }

    companion object {
        private var INSTANCE: LanguageServerWrapper? = null
        fun getInstance() = INSTANCE ?: LanguageServerWrapper().also { INSTANCE = it }
    }
}
