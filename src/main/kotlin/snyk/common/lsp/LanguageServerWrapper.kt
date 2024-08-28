package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getContentRootVirtualFiles
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.getWaitForResultsTimeout
import io.snyk.plugin.isSnykIaCLSEnabled
import io.snyk.plugin.isSnykOSSLSEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.toLanguageServerURL
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.CodeActionCapabilities
import org.eclipse.lsp4j.CodeLensCapabilities
import org.eclipse.lsp4j.CodeLensWorkspaceCapabilities
import org.eclipse.lsp4j.DiagnosticCapabilities
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
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
import org.jetbrains.concurrency.runAsync
import snyk.common.EnvironmentHelper
import snyk.common.getEndpointUrl
import snyk.common.lsp.commands.COMMAND_COPY_AUTH_LINK
import snyk.common.lsp.commands.COMMAND_EXECUTE_CLI
import snyk.common.lsp.commands.COMMAND_GET_ACTIVE_USER
import snyk.common.lsp.commands.COMMAND_GET_FEATURE_FLAG_STATUS
import snyk.common.lsp.commands.COMMAND_GET_SETTINGS_SAST_ENABLED
import snyk.common.lsp.commands.COMMAND_LOGIN
import snyk.common.lsp.commands.COMMAND_LOGOUT
import snyk.common.lsp.commands.COMMAND_REPORT_ANALYTICS
import snyk.common.lsp.commands.COMMAND_WORKSPACE_FOLDER_SCAN
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.io.path.exists

private const val INITIALIZATION_TIMEOUT = 20L

@Suppress("TooGenericExceptionCaught")
class LanguageServerWrapper(
    private val lsPath: String = getCliFile().absolutePath,
    private val executorService: ExecutorService = Executors.newCachedThreadPool(),
) : Disposable {
    private var authenticatedUser: Map<String, String>? = null
    private var initializeResult: InitializeResult? = null
    private val gson = Gson()
    private var disposed = false
        get() {
            return ApplicationManager.getApplication().isDisposed || field
        }

    fun isDisposed() = disposed

    val logger = Logger.getInstance("Snyk Language Server")

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

    var isInitializing: ReentrantLock = ReentrantLock()

    var isInitialized: Boolean = false

    @OptIn(DelicateCoroutinesApi::class)
    private fun initialize() {
        if (disposed) return
        if (lsPath.toNioPathOrNull()?.exists() == false) {
            val message = "Snyk Language Server not found. Please make sure the Snyk CLI is installed at $lsPath."
            logger.warn(message)
            return
        }
        try {
            val snykLanguageClient = SnykLanguageClient()
            languageClient = snykLanguageClient
            val logLevel =
                when {
                    snykLanguageClient.logger.isDebugEnabled -> "debug"
                    snykLanguageClient.logger.isTraceEnabled -> "trace"
                    else -> "info"
                }
            val cmd = listOf(lsPath, "language-server", "-l", logLevel)

            val processBuilder = ProcessBuilder(cmd)
            pluginSettings().token?.let { EnvironmentHelper.updateEnvironment(processBuilder.environment(), it) }

            process = processBuilder.start()
            launcher = LSPLauncher.createClientLauncher(languageClient, process.inputStream, process.outputStream)
            languageServer = launcher.remoteProxy

            GlobalScope.launch {
                if (!disposed) {
                    try {
                        process.errorStream.bufferedReader().forEachLine { println(it) }
                    } catch (ignored: RuntimeException) {
                        // ignore
                    }
                }
            }

            launcher.startListening()
            sendInitializeMessage()
            isInitialized = true
        } catch (e: Exception) {
            logger.warn(e)
            process.destroy()
            isInitialized = false
        }

        // update feature flags
        runAsync { pluginSettings().isGlobalIgnoresFeatureEnabled = isGlobalIgnoresFeatureEnabled() }
    }

    fun shutdown(): Future<*> =
        executorService.submit {
            if (process.isAlive) {
                languageServer.shutdown().get(1, TimeUnit.SECONDS)
                languageServer.exit()
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }

    private fun determineWorkspaceFolders(): List<WorkspaceFolder> {
        val workspaceFolders = mutableSetOf<WorkspaceFolder>()
        ProjectManager.getInstance().openProjects.forEach { project ->
            if (!project.isDisposed) {
                workspaceFolders.addAll(getWorkspaceFolders(project))
            }
        }
        return workspaceFolders.toList()
    }

    fun getWorkspaceFolders(project: Project): Set<WorkspaceFolder> {
        if (disposed || project.isDisposed) return emptySet()
        val normalizedRoots = getTrustedContentRoots(project)
        return normalizedRoots.map { WorkspaceFolder(it.toLanguageServerURL(), it.name) }.toSet()
    }

    private fun getTrustedContentRoots(project: Project): MutableSet<VirtualFile> {
        if (!confirmScanningAndSetWorkspaceTrustedStateIfNeeded(project)) return mutableSetOf()

        val contentRoots = project.getContentRootVirtualFiles()
        val trustService = service<WorkspaceTrustService>()
        val normalizedRoots = mutableSetOf<VirtualFile>()

        for (root in contentRoots) {
            val pathTrusted = trustService.isPathTrusted(root.toNioPath())
            if (!pathTrusted) {
                logger.debug("Path not trusted: ${root.path}")
                continue
            }

            var add = true
            for (normalizedRoot in normalizedRoots) {
                if (!root.path.startsWith(normalizedRoot.path)) continue
                add = false
                break
            }
            if (add) normalizedRoots.add(root)
        }
        logger.debug("Normalized content roots: $normalizedRoots")
        return normalizedRoots
    }

    fun sendInitializeMessage() {
        if (disposed) return
        val workspaceFolders = determineWorkspaceFolders()

        val params = InitializeParams()
        params.processId = ProcessHandle.current().pid().toInt()
        params.clientInfo = ClientInfo(pluginInfo.integrationEnvironment, pluginInfo.integrationEnvironmentVersion)
        params.initializationOptions = getSettings()
        params.workspaceFolders = workspaceFolders
        params.capabilities = getCapabilities()

        initializeResult = languageServer.initialize(params).get(INITIALIZATION_TIMEOUT, TimeUnit.SECONDS)
        languageServer.initialized(InitializedParams())
    }

    private fun getCapabilities(): ClientCapabilities =
        ClientCapabilities().let { clientCapabilities ->
            clientCapabilities.workspace =
                WorkspaceClientCapabilities().let { workspaceClientCapabilities ->
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
            clientCapabilities.textDocument =
                TextDocumentClientCapabilities().let {
                    it.codeLens = CodeLensCapabilities(true)
                    it.codeAction = CodeActionCapabilities(true)
                    it.diagnostic = DiagnosticCapabilities(true)

                    it
                }
            return clientCapabilities
        }

    fun updateWorkspaceFolders(
        added: Set<WorkspaceFolder>,
        removed: Set<WorkspaceFolder>,
    ) {
        if (disposed) return
        try {
            if (!ensureLanguageServerInitialized()) return
            val params = DidChangeWorkspaceFoldersParams()
            params.event = WorkspaceFoldersChangeEvent(added.toList(), removed.toList())
            languageServer.workspaceService.didChangeWorkspaceFolders(params)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun ensureLanguageServerInitialized(): Boolean {
        if (disposed) return false
        isInitializing.lock()
        assert(isInitializing.holdCount == 1)

        if (!isInitialized) {
            try {
                initialize()
            } catch (e: RuntimeException) {
                throw (e)
            }
        }
        isInitializing.unlock()
        return isInitialized
    }

    fun sendReportAnalyticsCommand(scanDoneEvent: ScanDoneEvent) {
        if (!ensureLanguageServerInitialized()) return
        try {
            val eventString = gson.toJson(scanDoneEvent)
            val param = ExecuteCommandParams()
            param.command = COMMAND_REPORT_ANALYTICS
            param.arguments = listOf(eventString)
            languageServer.workspaceService.executeCommand(param)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun sendScanCommand(project: Project) {
        if (!ensureLanguageServerInitialized()) return
        DumbService.getInstance(project).runWhenSmart {
            getTrustedContentRoots(project).forEach {
                sendFolderScanCommand(it.path, project)
            }
        }
    }

    fun getFeatureFlagStatus(featureFlag: String): Boolean {
        if (!ensureLanguageServerInitialized()) return false
        return getFeatureFlagStatusInternal(featureFlag)
    }

    private fun getFeatureFlagStatusInternal(featureFlag: String): Boolean {
        if (pluginSettings().token.isNullOrBlank()) {
            return false
        }

        try {
            val param = ExecuteCommandParams()
            param.command = COMMAND_GET_FEATURE_FLAG_STATUS
            param.arguments = listOf(featureFlag)
            val result = languageServer.workspaceService.executeCommand(param).get(10, TimeUnit.SECONDS)

            val resultMap = result as? Map<*, *>
            val ok = resultMap?.get("ok") as? Boolean ?: false
            val userMessage = resultMap?.get("userMessage") as? String ?: "No message provided"

            if (ok) {
                logger.info("Feature flag $featureFlag is enabled.")
                return true
            } else {
                logger.info("Feature flag $featureFlag is disabled. Message: $userMessage")
                return false
            }
        } catch (e: Exception) {
            logger.warn("Error while checking feature flag: ${e.message}", e)
            return false
        }
    }

    fun sendFolderScanCommand(
        folder: String,
        project: Project,
    ) {
        if (!ensureLanguageServerInitialized()) return
        if (DumbService.getInstance(project).isDumb) return
        try {
            val param = ExecuteCommandParams()
            param.command = COMMAND_WORKSPACE_FOLDER_SCAN
            param.arguments = listOf(folder)
            languageServer.workspaceService.executeCommand(param)
        } catch (ignored: Exception) {
            // do nothing to not break UX for analytics
        }
    }

    fun getSettings(): LanguageServerSettings {
        val ps = pluginSettings()
        val authMethod =
            if (ps.useTokenAuthentication) {
                "token"
            } else {
                "oauth"
            }
        return LanguageServerSettings(
            activateSnykOpenSource = (isSnykOSSLSEnabled() && ps.ossScanEnable).toString(),
            activateSnykCodeSecurity = ps.snykCodeSecurityIssuesScanEnable.toString(),
            activateSnykCodeQuality = ps.snykCodeQualityIssuesScanEnable.toString(),
            activateSnykIac = isSnykIaCLSEnabled().toString(),
            organization = ps.organization,
            insecure = ps.ignoreUnknownCA.toString(),
            endpoint = getEndpointUrl(),
            cliPath = getCliFile().absolutePath,
            token = ps.token,
            filterSeverity =
            SeverityFilter(
                critical = ps.criticalSeverityEnabled,
                high = ps.highSeverityEnabled,
                medium = ps.mediumSeverityEnabled,
                low = ps.lowSeverityEnabled,
            ),
            enableTrustedFoldersFeature = "false",
            scanningMode = if (!ps.scanOnSave) "manual" else "auto",
            integrationName = pluginInfo.integrationName,
            integrationVersion = pluginInfo.integrationVersion,
            authenticationMethod = authMethod,
            enableSnykOSSQuickFixCodeActions = "true",
        )
    }

    fun updateConfiguration() {
        if (!ensureLanguageServerInitialized()) return
        val params = DidChangeConfigurationParams(getSettings())
        languageServer.workspaceService.didChangeConfiguration(params)

        if (pluginSettings().scanOnSave) {
            ProjectManager.getInstance().openProjects.forEach { sendScanCommand(it) }
        }
    }

    fun getAuthenticatedUser(): String? {
        if (pluginSettings().token.isNullOrBlank()) return null
        if (!ensureLanguageServerInitialized()) return null

        if (!this.authenticatedUser.isNullOrEmpty()) return authenticatedUser!!["username"]
        val cmd = ExecuteCommandParams(COMMAND_GET_ACTIVE_USER, emptyList())
        val result =
            try {
                languageServer.workspaceService.executeCommand(cmd).get(5, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                logger.warn("could not retrieve authenticated user", e)
                null
            }
        if (result != null) {
            @Suppress("UNCHECKED_CAST")
            this.authenticatedUser = result as Map<String, String>?
            return result["username"]
        }
        return null
    }

    // triggers login and returns auth URL
    fun login(): String? {
        if (!ensureLanguageServerInitialized()) return null
        try {
            val loginCmd = ExecuteCommandParams(COMMAND_LOGIN, emptyList())
            pluginSettings().token =
                languageServer.workspaceService
                    .executeCommand(loginCmd)
                    .get(120, TimeUnit.SECONDS)
                    ?.toString() ?: ""
        } catch (e: TimeoutException) {
            logger.warn("could not login", e)
        }
        return pluginSettings().token
    }

    fun getAuthLink(): String? {
        if (!ensureLanguageServerInitialized()) return null
        try {
            val authLinkCmd = ExecuteCommandParams(COMMAND_COPY_AUTH_LINK, emptyList())
            val url =
                languageServer.workspaceService
                    .executeCommand(authLinkCmd)
                    .get(10, TimeUnit.SECONDS)
                    .toString()
            return url
        } catch (e: TimeoutException) {
            logger.warn("could not login", e)
            return null
        }
    }

    fun logout() {
        if (!ensureLanguageServerInitialized()) return
        val cmd = ExecuteCommandParams(COMMAND_LOGOUT, emptyList())
        try {
            languageServer.workspaceService.executeCommand(cmd).get(5, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            logger.warn("could not logout", e)
        }
    }

    fun addContentRoots(project: Project) {
        if (disposed || project.isDisposed) return
        assert(isInitialized)
        ensureLanguageServerProtocolVersion(project)
        val added = getWorkspaceFolders(project)
        updateWorkspaceFolders(added, emptySet())
    }

    fun isGlobalIgnoresFeatureEnabled(): Boolean {
        if (!ensureLanguageServerInitialized()) return false
        return this.getFeatureFlagStatus("snykCodeConsistentIgnores")
    }

    private fun ensureLanguageServerProtocolVersion(project: Project) {
        val protocolVersion = initializeResult?.serverInfo?.version
        pluginSettings().currentLSProtocolVersion = protocolVersion?.toIntOrNull()

        if ((protocolVersion ?: "") == pluginSettings().requiredLsProtocolVersion.toString()) {
            return
        }
        getSnykTaskQueueService(project)?.waitUntilCliDownloadedIfNeeded()
    }


    data class SastSettings(
        val sastEnabled: Boolean,
        val localCodeEngine: LocalCodeEngine,
        val org: String? = null,
        val supportedLanguages: List<String>,
        val reportFalsePositivesEnabled: Boolean,
        val autofixEnabled: Boolean,
    )

    data class LocalCodeEngine(val allowCloudUpload: Boolean, val url: String, val enabled: Boolean)

    @Suppress("UNCHECKED_CAST")
    fun getSastSettings(): SastSettings? {
        if (!ensureLanguageServerInitialized()) return null
        try {
            val executeCommandParams = ExecuteCommandParams(COMMAND_GET_SETTINGS_SAST_ENABLED, emptyList())
            val response =
                languageServer.workspaceService.executeCommand(executeCommandParams).get(10, TimeUnit.SECONDS)
            if (response is Map<*, *>) {
                val localCodeEngineMap: Map<String, *> = response["localCodeEngine"] as Map<String, *>
                return SastSettings(
                    sastEnabled = response["sastEnabled"] as Boolean,
                    localCodeEngine = LocalCodeEngine(
                        allowCloudUpload = localCodeEngineMap["allowCloudUpload"] as Boolean,
                        url = localCodeEngineMap["url"] as String,
                        enabled = localCodeEngineMap["enabled"] as Boolean,
                    ),
                    org = response["org"] as String?,
                    reportFalsePositivesEnabled = response["reportFalsePositivesEnabled"] as Boolean,
                    autofixEnabled = response["autofixEnabled"] as Boolean,
                    supportedLanguages = response["supportedLanguages"] as List<String>,
                )
            }
        } catch (e: TimeoutException) {
            logger.debug(e)
        }
        return null
    }

    fun executeCLIScan(cmds: List<String>, path: String): String {
        if (!ensureLanguageServerInitialized()) throw RuntimeException("couldn't initialize language server")
        // this will fail on some multi-module projects, but we will move to explicit calls anyway
        // and this is just a stop gap
        val args: List<String> = mutableListOf(path, *cmds.toTypedArray())
        val executeCommandParams = ExecuteCommandParams(COMMAND_EXECUTE_CLI, args)

        val timeoutMs = getWaitForResultsTimeout()
        val response =
            languageServer.workspaceService.executeCommand(executeCommandParams).get(timeoutMs, TimeUnit.MILLISECONDS)
        if (response is Map<*, *>) {
            return response["stdOut"] as String
        }
        return ""
    }

    companion object {

        private var instance: LanguageServerWrapper? = null
        fun getInstance() =
            instance ?: LanguageServerWrapper().also {
                Disposer.register(SnykPluginDisposable.getInstance(), it)
                instance = it
            }
    }

    override fun dispose() {
        disposed = true
        shutdown()
    }

}

