package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.ide.impl.ProjectUtil
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
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.runInBackground
import io.snyk.plugin.toLanguageServerURL
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
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
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageServer
import org.jetbrains.concurrency.runAsync
import snyk.common.EnvironmentHelper
import snyk.common.getEndpointUrl
import snyk.common.lsp.analytics.AbstractAnalyticsEvent
import snyk.common.lsp.commands.COMMAND_CODE_FIX_APPLY_AI_EDIT
import snyk.common.lsp.commands.COMMAND_CODE_FIX_DIFFS
import snyk.common.lsp.commands.COMMAND_COPY_AUTH_LINK
import snyk.common.lsp.commands.COMMAND_EXECUTE_CLI
import snyk.common.lsp.commands.COMMAND_GET_ACTIVE_USER
import snyk.common.lsp.commands.COMMAND_GET_FEATURE_FLAG_STATUS
import snyk.common.lsp.commands.COMMAND_GET_SETTINGS_SAST_ENABLED
import snyk.common.lsp.commands.COMMAND_LOGIN
import snyk.common.lsp.commands.COMMAND_LOGOUT
import snyk.common.lsp.commands.COMMAND_REPORT_ANALYTICS
import snyk.common.lsp.commands.COMMAND_WORKSPACE_FOLDER_SCAN
import snyk.common.lsp.commands.SNYK_GENERATE_ISSUE_DESCRIPTION
import snyk.common.lsp.progress.ProgressManager
import snyk.common.lsp.settings.LanguageServerSettings
import snyk.common.lsp.settings.SeverityFilter
import snyk.common.removeTrailingSlashesIfPresent
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.io.FileNotFoundException
import java.nio.file.Paths
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger.getLogger
import kotlin.io.path.exists

private const val INITIALIZATION_TIMEOUT = 20L

@Suppress("TooGenericExceptionCaught")
class LanguageServerWrapper(
    private val lsPath: String = getCliFile().absolutePath,
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
) : Disposable {
    private var authenticatedUser: Map<String, String>? = null
    private var initializeResult: InitializeResult? = null
    private val gson = Gson()
    // internal for test set up
    internal val configuredWorkspaceFolders: MutableSet<WorkspaceFolder> = Collections.synchronizedSet(mutableSetOf())
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
            EnvironmentHelper.updateEnvironment(processBuilder.environment(), pluginSettings().token ?: "")

            process = processBuilder.start()

            runAsync {
                if (!disposed) {
                    try {
                        process.errorStream.bufferedReader().forEachLine { logger.debug(it) }
                    } catch (ignored: Exception) {
                        // ignore
                    }
                }
            }

            // enable message logging
            val wrapper = fun(wrapped: MessageConsumer): MessageConsumer {
                return MessageConsumer { message ->
                    logger.trace(message.toString())
                    wrapped.consume(message)
                }
            }

            launcher = LSPLauncher.createClientLauncher(
                snykLanguageClient,
                process.inputStream,
                process.outputStream,
                executorService,
                wrapper
            )

            languageServer = launcher.remoteProxy

            val listenerFuture = launcher.startListening()

            runAsync {
                listenerFuture.get()
                logger.info("Snyk Language Server was terminated, listener has ended.")
                isInitialized = false
            }

            if (!(listenerFuture.isDone || listenerFuture.isCancelled)) {
                configuredWorkspaceFolders.clear()
                sendInitializeMessage()
                isInitialized = true
                // listen for downloads / restarts
                LanguageServerRestartListener.getInstance()
                refreshFeatureFlags()
            } else {
                logger.warn("Language Server initialization did not succeed")
            }
        } catch (e: Exception) {
            logger.warn(e)
            process.destroy()
            isInitialized = false
        }
    }

    fun shutdown() {
        // LSP4j logs errors and rethrows - this is bad practice, and we don't need that log here, so we shut it up.
        val lsp4jLogger = getLogger(RemoteEndpoint::class.java.name)
        val messageProducerLogger = getLogger(StreamMessageProducer::class.java.name)
        val previousMessageProducerLevel = messageProducerLogger.level
        val previousLSP4jLogLevel = lsp4jLogger.level
        lsp4jLogger.level = Level.OFF
        messageProducerLogger.level = Level.OFF
        try {
            val shouldShutdown = lsIsAlive()
            executorService.submit {
                if (shouldShutdown) {
                    val project = ProjectUtil.getActiveProject()
                    if (project != null) {
                        getSnykTaskQueueService(project)?.stopScan()
                    }
                    // cancel all progresses, as we can have more progresses than just scans
                    ProgressManager.getInstance().cancelProgresses()
                    languageServer.shutdown().get(1, TimeUnit.SECONDS)
                }
            }
        } catch (ignored: Exception) {
            // we don't care
        } finally {
            try {
                if (lsIsAlive()) languageServer.exit()
            } catch (ignore: Exception) {
                // do nothing
            } finally {
                if (lsIsAlive()) process.destroyForcibly()
            }
            lsp4jLogger.level = previousLSP4jLogLevel
            messageProducerLogger.level = previousMessageProducerLevel
            configuredWorkspaceFolders.clear()
        }
    }

    private fun lsIsAlive() = ::process.isInitialized && process.isAlive

    fun getWorkspaceFoldersFromRoots(project: Project): Set<WorkspaceFolder> {
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
            val pathTrusted = try {
                trustService.isPathTrusted(root.toNioPath())
            } catch (e: UnsupportedOperationException) {
                // this must be temp filesystem so the path mapping doesn't work
                continue
            }

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

        val params = InitializeParams()
        params.processId = ProcessHandle.current().pid().toInt()
        params.clientInfo = ClientInfo(pluginInfo.integrationEnvironment, pluginInfo.integrationEnvironmentVersion)
        params.initializationOptions = getSettings()
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
            params.event = WorkspaceFoldersChangeEvent(
                added.filter { !configuredWorkspaceFolders.contains(it) },
                removed.filter { configuredWorkspaceFolders.contains(it) },
            )
            if (params.event.added.size > 0 || params.event.removed.size > 0) {
                languageServer.workspaceService.didChangeWorkspaceFolders(params)
                configuredWorkspaceFolders.removeAll(removed)
                configuredWorkspaceFolders.addAll(added)
            }
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun ensureLanguageServerInitialized(): Boolean {
        if (disposed) return false
        try {
            isInitializing.lock()
            if (isInitializing.holdCount > 1) {
                val message =
                    "Snyk failed to initialize. This is an unexpected loop error, please contact " +
                        "Snyk support with the error message.\n\n" + RuntimeException().stackTraceToString()
                logger.error(message)
                return false
            }

            if (!isInitialized) {
                try {
                    initialize()
                } catch (e: RuntimeException) {
                    throw (e)
                }
            }
        } finally {
            isInitializing.unlock()
        }
        return isInitialized
    }

    fun sendReportAnalyticsCommand(event: AbstractAnalyticsEvent) {
        if (notAuthenticated()) return
        try {
            val eventString = gson.toJson(event)
            val param = ExecuteCommandParams()
            param.command = COMMAND_REPORT_ANALYTICS
            param.arguments = listOf(eventString)
            languageServer.workspaceService.executeCommand(param)
        } catch (e: Exception) {
            logger.error(e)
        }
    }

    fun sendScanCommand(project: Project) {
        if (notAuthenticated()) return
        DumbService.getInstance(project).runWhenSmart {
            getTrustedContentRoots(project).forEach {
                addContentRoots(project)
                sendFolderScanCommand(it.path, project)
            }
        }
    }

    fun refreshFeatureFlags() {
        runAsync {
            // this check should be async, as refresh is called from initialization and notAuthenticated is triggering
            // initialization. So, to make it wait patiently for its turn, it needs to be checked and executed in a
            // different thread.
            if (notAuthenticated()) return@runAsync
            pluginSettings().isGlobalIgnoresFeatureEnabled = getFeatureFlagStatus("snykCodeConsistentIgnores")
        }
    }

    private fun getFeatureFlagStatus(@Suppress("SameParameterValue") featureFlag: String): Boolean {
        if (notAuthenticated()) return false
        try {
            val param = ExecuteCommandParams()
            param.command = COMMAND_GET_FEATURE_FLAG_STATUS
            param.arguments = listOf(featureFlag)
            val result = try {
                executeCommand(param)
            } catch (e: TimeoutException) {
                // retry with 30s timeout
                Thread.sleep(5000)
                executeCommand(param, 30000)
            }

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
        } catch (t: TimeoutException) {
            logger.warn("Timeout while retrieving feature flag: ${t.message}")
            return false
        } catch (e: Exception) {
            logger.warn("Error while checking feature flag: ${e.message}", e)
            return false
        }
    }

    private fun executeCommand(param: ExecuteCommandParams, timeoutMillis: Long = 5000): Any? =
        languageServer.workspaceService.executeCommand(param).get(timeoutMillis, TimeUnit.MILLISECONDS)

    fun sendFolderScanCommand(
        folder: String,
        project: Project,
    ) {
        if (notAuthenticated()) return
        if (DumbService.getInstance(project).isDumb) return
        try {
            val folderUri = Paths.get(folder).toUri().toASCIIString().removeTrailingSlashesIfPresent()
            if (!configuredWorkspaceFolders.any { it.uri.removeTrailingSlashesIfPresent() == folderUri }) return
            val param = ExecuteCommandParams()
            param.command = COMMAND_WORKSPACE_FOLDER_SCAN
            param.arguments = listOf(folder)
            languageServer.workspaceService.executeCommand(param)
        } catch (e: FileNotFoundException) {
            logger.debug("File not found: $folder")
        } catch (e: Exception) {
            logger.error("error calling scan command from language server. re-initializing", e)
            restart()
        }
    }

    fun restart() {
        runInBackground("Snyk: restarting language server...") {
            shutdown()
            Thread.sleep(1000)
            ensureLanguageServerInitialized()
            ProjectManager.getInstance().openProjects.forEach { project -> addContentRoots(project) }
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
            activateSnykOpenSource = ps.ossScanEnable.toString(),
            activateSnykCodeSecurity = ps.snykCodeSecurityIssuesScanEnable.toString(),
            activateSnykCodeQuality = ps.snykCodeQualityIssuesScanEnable.toString(),
            activateSnykIac = ps.iacScanEnabled.toString(),
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

    fun updateConfiguration(runScan: Boolean = false) {
        if (!ensureLanguageServerInitialized()) return
        val params = DidChangeConfigurationParams(getSettings())
        languageServer.workspaceService.didChangeConfiguration(params)

        if (runScan && pluginSettings().scanOnSave) {
            ProjectManager.getInstance().openProjects.forEach { sendScanCommand(it) }
        }
    }

    fun getAuthenticatedUser(): String? {
        if (notAuthenticated()) return null

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

    fun generateIssueDescription(issue: ScanIssue): String? {
        if (notAuthenticated()) return null
        val key = issue.additionalData.key
        if (key.isBlank()) throw RuntimeException("Issue ID is required")
        val generateIssueCommand = ExecuteCommandParams(SNYK_GENERATE_ISSUE_DESCRIPTION, listOf(key))
        return try {
            executeCommand(generateIssueCommand, Long.MAX_VALUE).toString()
        } catch (e: TimeoutException) {
            val exceptionMessage = "generate issue description failed"
            logger.warn(exceptionMessage, e)
            null
        } catch (e: Exception) {
            if (e.message?.contains("failed to find issue") == true) {
                val msg = "The issue is not in the server cache anymore, please wait for any running scans to finish"
                logger.debug(msg)
                return msg
            } else {
                logger.error("generate issue description failed", e)
                null
            }
        }
    }

    fun logout() {
        if (!ensureLanguageServerInitialized()) return
        val cmd = ExecuteCommandParams(COMMAND_LOGOUT, emptyList())
        try {
            executeCommand(cmd)
        } catch (e: TimeoutException) {
            logger.warn("could not logout", e)
        }
    }

    fun addContentRoots(project: Project) {
        if (disposed || project.isDisposed) return
        ensureLanguageServerInitialized()
        ensureLanguageServerProtocolVersion(project)
        updateConfiguration(false)
        val added = getWorkspaceFoldersFromRoots(project)
        updateWorkspaceFolders(added, emptySet())
    }

    @Suppress("UNCHECKED_CAST")
    fun sendCodeFixDiffsCommand(folderURI: String, fileURI: String, issueID: String): List<Fix> {
        if (notAuthenticated()) return emptyList()

        try {
            val param = ExecuteCommandParams()
            param.command = COMMAND_CODE_FIX_DIFFS
            param.arguments = listOf(folderURI, fileURI, issueID)
            val executeCommandResult = executeCommand(param, 120000) ?: return emptyList()

            val diffList: MutableList<Fix> = mutableListOf()
            val result = executeCommandResult as List<*>
            result.forEach {
                val entry = it as Map<String, *>
                val fixId = entry["fixId"] as? String
                val unifiedDiffsPerFile = entry["unifiedDiffsPerFile"] as? Map<String, String>

                if (fixId != null && unifiedDiffsPerFile != null) {
                    val fix = Fix(fixId, unifiedDiffsPerFile)
                    diffList.add(fix)
                }
            }
            return diffList
        } catch (err: Exception) {
            logger.warn("Error in sendCodeFixDiffsCommand", err)
            return emptyList()
        }
    }

    fun sendCodeApplyAiFixEditCommand(fixId: String) {
        if (notAuthenticated()) return
        val param = ExecuteCommandParams()
        param.command = COMMAND_CODE_FIX_APPLY_AI_EDIT
        param.arguments = listOf(fixId)
        executeCommand(param)
    }

    fun notAuthenticated() = !ensureLanguageServerInitialized() || pluginSettings().token.isNullOrBlank()


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
            val response = executeCommand(executeCommandParams, 10000)
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

    override fun dispose() {
        disposed = true
        shutdown()
    }


    companion object {
        private var instance: LanguageServerWrapper? = null
        fun getInstance() =
            instance ?: LanguageServerWrapper().also {
                Disposer.register(SnykPluginDisposable.getInstance(), it)
                instance = it
            }
    }

}
