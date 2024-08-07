package snyk.common.lsp

import com.google.common.util.concurrent.CycleDetectingLockFactory
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
import io.snyk.plugin.isSnykIaCLSEnabled
import io.snyk.plugin.isSnykOSSLSEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.toLanguageServerURL
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
import snyk.common.isOauth
import snyk.common.lsp.commands.ScanDoneEvent
import snyk.pluginInfo
import snyk.trust.WorkspaceTrustService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.io.IOException
import java.net.URI
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
    private val gson = com.google.gson.Gson()
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

    @Suppress("MemberVisibilityCanBePrivate") // because we want to test it
    var isInitializing: ReentrantLock =
        CycleDetectingLockFactory
            .newInstance(CycleDetectingLockFactory.Policies.THROW)
            .newReentrantLock("initializeLock")

    internal val isInitialized: Boolean
        get() =
            ::languageClient.isInitialized &&
                ::languageServer.isInitialized &&
                ::process.isInitialized &&
                process.info().startInstant().isPresent &&
                process.isAlive &&
                !isInitializing.isLocked

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
                    } catch (ignored: IOException) {
                        // ignore
                    }
                }
            }

            launcher.startListening()
            sendInitializeMessage()
        } catch (e: Exception) {
            logger.warn(e)
            process.destroy()
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

        // the sort is to ensure that parent folders come first
        // e.g. /a/b should come before /a/b/c
        val contentRoots = project.getContentRootVirtualFiles().filterNotNull().sortedBy { it.path }
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
        if (!isInitialized) {
            try {
                assert(isInitializing.holdCount == 0)
                isInitializing.lock()
                initialize()
            } finally {
                isInitializing.unlock()
            }
        }
        return isInitialized
    }

    fun sendReportAnalyticsCommand(scanDoneEvent: ScanDoneEvent) {
        if (!ensureLanguageServerInitialized()) return
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
            param.command = "snyk.getFeatureFlagStatus"
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
            param.command = "snyk.workspaceFolder.scan"
            param.arguments = listOf(folder)
            languageServer.workspaceService.executeCommand(param)
        } catch (ignored: Exception) {
            // do nothing to not break UX for analytics
        }
    }

    fun getSettings(): LanguageServerSettings {
        val ps = pluginSettings()
        val authMethod =
            if (URI(getEndpointUrl()).isOauth()) {
                "oauth"
            } else {
                "token"
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
        val cmd = ExecuteCommandParams("snyk.getActiveUser", emptyList())
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
