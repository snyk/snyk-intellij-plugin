package snyk.common.lsp

import com.google.gson.Gson
import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.queryParameters
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykFolderConfigListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykScanSummaryListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.events.SnykShowIssueDetailListener
import io.snyk.plugin.events.SnykShowIssueDetailListener.Companion.SHOW_DETAIL_ACTION
import io.snyk.plugin.events.SnykTreeViewListener
import io.snyk.plugin.getDecodedParam
import io.snyk.plugin.getDocument
import io.snyk.plugin.getSafeOffset
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.navigateToSource
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsync
import io.snyk.plugin.refreshAnnotationsForFile
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.sha256
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.toVirtualFileOrNull
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.LanguageClient
import org.jetbrains.concurrency.runAsync
import snyk.common.ProductType
import snyk.common.editor.DocumentChanger
import snyk.common.lsp.progress.ProgressManager
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.sdk.SdkHelper
import snyk.trust.WorkspaceTrustService

/** Processes Language Server requests and notifications from the server to the IDE */
@Suppress("unused")
class SnykLanguageClient(private val project: Project, val progressManager: ProgressManager) :
  LanguageClient, Disposable {
  val logger = Logger.getInstance("Snyk Language Server")
  val gson = Gson()

  private var disposed = false
    get() {
      return project.isDisposed || field
    }

  fun isDisposed() = disposed

  override fun telemetryEvent(`object`: Any?) {
    // do nothing
  }

  override fun notifyProgress(params: ProgressParams) {
    progressManager.notifyProgress(params)
  }

  @JsonNotification("$/snyk.publishDiagnostics316")
  fun publishDiagnostics316(diagnosticsParams: PublishDiagnosticsParams?) = Unit

  override fun publishDiagnostics(diagnosticsParams: PublishDiagnosticsParams?) {
    if (diagnosticsParams == null) {
      return
    }

    val filePath = diagnosticsParams.uri

    // Run async to avoid blocking the LSP message thread
    org.jetbrains.concurrency.runAsync {
      try {
        getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)?.let {
          updateCache(project, filePath, diagnosticsParams, it)
        }
      } catch (e: Exception) {
        logger.error("Error publishing the new diagnostics", e)
      }
    }
  }

  private fun updateCache(
    project: Project,
    filePath: String,
    diagnosticsParams: PublishDiagnosticsParams,
    scanPublisher: SnykScanListener,
  ) {
    if (disposed) return

    // Handle case where file no longer exists (e.g., temporary files)
    val virtualFile = filePath.toVirtualFileOrNull()
    if (virtualFile == null) {
      logger.debug("File not found for diagnostics: $filePath")
      return
    }

    val snykFile = SnykFile(project, virtualFile)
    val firstDiagnostic = diagnosticsParams.diagnostics.firstOrNull()
    val product = firstDiagnostic?.source

    // If the diagnostics for the file is empty, clear the cache.
    if (firstDiagnostic == null) {
      scanPublisher.onPublishDiagnostics(LsProduct.Code, snykFile, emptySet())
      scanPublisher.onPublishDiagnostics(LsProduct.OpenSource, snykFile, emptySet())
      scanPublisher.onPublishDiagnostics(LsProduct.InfrastructureAsCode, snykFile, emptySet())
      return
    }

    val issues = getScanIssues(diagnosticsParams)
    if (product != null) {
      scanPublisher.onPublishDiagnostics(LsProduct.getFor(product), snykFile, issues)
    }

    return
  }

  fun getScanIssues(diagnosticsParams: PublishDiagnosticsParams): Set<ScanIssue> {
    val issues =
      diagnosticsParams.diagnostics
        .stream()
        .map {
          val issue = gson.fromJson(it.data.toString(), ScanIssue::class.java)
          // load textRange for issue so it doesn't happen in UI thread
          issue.textRange
          if (issue.isIgnored() && !pluginSettings().isGlobalIgnoresFeatureEnabled) {
            // apparently the server has consistent ignores activated
            pluginSettings().isGlobalIgnoresFeatureEnabled = true
          }
          issue.project = project
          issue
        }
        .collect(Collectors.toSet())

    return issues
  }

  override fun applyEdit(
    params: ApplyWorkspaceEditParams?
  ): CompletableFuture<ApplyWorkspaceEditResponse> {
    val falseFuture = CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(false))
    if (disposed) return falseFuture

    WriteCommandAction.runWriteCommandAction(project) {
      params?.edit?.changes?.forEach {
        DocumentChanger.applyChange(it)
        refreshAnnotationsForFile(project, it.key.toVirtualFile())
      }
    }

    return CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(true))
  }

  override fun refreshCodeLenses(): CompletableFuture<Void> = refreshUI()

  override fun refreshInlineValues(): CompletableFuture<Void> = refreshUI()

  private fun refreshUI(): CompletableFuture<Void> {
    val completedFuture: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
    if (disposed) return completedFuture
    // No need for ReadAction wrapper - refreshAnnotationsForOpenFiles handles its own threading
    if (!project.isDisposed) {
      refreshAnnotationsForOpenFiles(project)
    }
    return completedFuture
  }

  @JsonNotification(value = "$/snyk.folderConfigs")
  fun folderConfig(folderConfigParam: FolderConfigsParam?) {
    if (disposed) return
    val folderConfigs = folderConfigParam?.folderConfigs ?: emptyList()
    runAsync {
      val service = service<FolderConfigSettings>()
      val languageServerWrapper = LanguageServerWrapper.getInstance(project)

      service.addAll(folderConfigs)
      folderConfigs.forEach { languageServerWrapper.updateFolderConfigRefresh(it.folderPath, true) }

      // Migrate any nested folder configs that may have been created by earlier plugin versions
      // Only workspace folder paths (non-nested) should have folder configs
      service.migrateNestedFolderConfigs(project)

      try {
        // Already in runAsync, so just use sync publisher here
        getSyncPublisher(project, SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC)
          ?.folderConfigsChanged(folderConfigs.isNotEmpty())
      } catch (e: Exception) {
        logger.error("Error processing snyk folder configs", e)
      }
    }
  }

  @JsonNotification(value = "$/snyk.scan")
  fun snykScan(snykScan: SnykScanParams) {
    if (disposed) return
    // Run async to avoid blocking the LSP message thread
    org.jetbrains.concurrency.runAsync {
      try {
        getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)?.let {
          processSnykScan(snykScan, it)
        }
      } catch (e: Exception) {
        logger.error("Error processing snyk scan", e)
      }
    }
  }

  private fun processSnykScan(snykScan: SnykScanParams, scanPublisher: SnykScanListener) {
    val product =
      when (LsProduct.getFor(snykScan.product)) {
        LsProduct.Code -> ProductType.CODE_SECURITY
        LsProduct.OpenSource -> ProductType.OSS
        LsProduct.InfrastructureAsCode -> ProductType.IAC
        else -> return
      }
    val key = ScanInProgressKey(snykScan.folderPath.toVirtualFile(), product)
    when (snykScan.status) {
      LsScanState.InProgress.value -> {
        if (ScanState.scanInProgress[key] == true) return
        ScanState.scanInProgress[key] = true
        scanPublisher.scanningStarted(snykScan)
      }
      LsScanState.Success.value -> {
        ScanState.scanInProgress[key] = false
        processSuccessfulScan(snykScan, scanPublisher)
      }
      LsScanState.Error.value -> {
        ScanState.scanInProgress[key] = false
        scanPublisher.scanningError(snykScan)
      }
    }
  }

  private fun processSuccessfulScan(snykScan: SnykScanParams, scanPublisher: SnykScanListener) {
    logger.debug("Scan completed")

    when (LsProduct.getFor(snykScan.product)) {
      LsProduct.OpenSource -> scanPublisher.scanningOssFinished()
      LsProduct.Code -> scanPublisher.scanningSnykCodeFinished()
      LsProduct.InfrastructureAsCode -> scanPublisher.scanningIacFinished()
      LsProduct.Unknown -> Unit
    }
  }

  @JsonNotification(value = "$/snyk.scanSummary")
  fun snykScanSummary(summaryParams: SnykScanSummaryParams) {
    if (disposed) return
    publishAsync(project, SnykScanSummaryListener.SNYK_SCAN_SUMMARY_TOPIC) {
      onSummaryReceived(summaryParams)
    }
  }

  @JsonNotification(value = "$/snyk.treeView")
  fun snykTreeView(params: SnykTreeViewParams) {
    if (disposed) return
    publishAsync(project, SnykTreeViewListener.SNYK_TREE_VIEW_TOPIC) { onTreeViewReceived(params) }
  }

  @JsonNotification(value = "$/snyk.hasAuthenticated")
  fun hasAuthenticated(param: HasAuthenticatedParam) {
    if (disposed) return

    // Always cancel pending login to close auth dialog, even if token unchanged
    val wrapper = LanguageServerWrapper.getInstance(project)
    wrapper.cancelPreviousLogin()

    val oldToken = pluginSettings().token ?: ""
    val oldApiUrl = pluginSettings().customEndpointUrl
    if (oldToken == param.token && oldApiUrl == param.apiUrl) return

    if (!param.apiUrl.isNullOrBlank()) {
      pluginSettings().customEndpointUrl = param.apiUrl
    }

    logger.info(
      "received authentication information: Token-Length: ${param.token?.length}}, URL: ${param.apiUrl}"
    )
    logger.info("auth type: ${pluginSettings().authenticationType.languageServerSettingsName}")
    logger.debug("is same token?  ${oldToken == param.token}")
    logger.debug("old-token-hash: ${oldToken.sha256()}, new-token-hash: ${param.token?.sha256()}")

    pluginSettings().token = param.token

    // we use internal API here, as we need to force immediate persistence to ensure new
    // refresh tokens are always persisted, not only every 5 min.
    StoreUtil.saveSettings(ApplicationManager.getApplication(), true)
    logger.debug("force-saved settings")

    // Notify listeners that settings have changed (including token update)
    publishAsync(project, SnykSettingsListener.SNYK_SETTINGS_TOPIC) { settingsChanged() }

    if (oldToken.isBlank() && !param.token.isNullOrBlank() && pluginSettings().scanOnSave) {
      wrapper.sendScanCommand()
    }
  }

  @JsonRequest(value = "workspace/snyk.sdks")
  fun getSdks(workspaceFolder: WorkspaceFolder): CompletableFuture<List<LsSdk>> =
    CompletableFuture.completedFuture(SdkHelper.getSdks(project))

  @JsonNotification(value = "$/snyk.addTrustedFolders")
  fun addTrustedPaths(param: SnykTrustedFoldersParams) {
    if (disposed) return
    val trustService = service<WorkspaceTrustService>()
    param.trustedFolders.forEach {
      it.toNioPathOrNull()?.let { path -> trustService.addTrustedPath(path) }
    }
  }

  override fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void> =
    progressManager.createProgress(params)

  override fun logTrace(params: LogTraceParams?) {
    if (disposed) return
    logger.debug(params?.message)
  }

  override fun showMessage(messageParams: MessageParams?) {
    if (disposed) return
    when (messageParams?.type) {
      MessageType.Error -> {
        val m = cutMessage(messageParams)
        SnykBalloonNotificationHelper.showError(m, project)
      }
      MessageType.Warning -> {
        val m = cutMessage(messageParams)
        SnykBalloonNotificationHelper.showWarn(m, project)
      }
      MessageType.Info -> {
        val notification = SnykBalloonNotificationHelper.showInfo(messageParams.message, project)
        AppExecutorUtil.getAppScheduledExecutorService()
          .schedule(
            {
              ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                  notification.expire()
                }
              }
            },
            30,
            TimeUnit.SECONDS,
          )
      }
      MessageType.Log -> logger.debug(messageParams.message)
      null -> {}
    }
  }

  private fun cutMessage(messageParams: MessageParams): String =
    if (messageParams.message.length > 500) {
      messageParams.message.substring(0, 500) + "..."
    } else {
      messageParams.message
    }

  override fun showMessageRequest(
    requestParams: ShowMessageRequestParams
  ): CompletableFuture<MessageActionItem> {
    val completedFuture = CompletableFuture.completedFuture(MessageActionItem(""))
    if (disposed) return completedFuture

    showMessageRequestFutures.clear()
    val actions =
      requestParams.actions
        .map {
          object : AnAction(it.title) {
            override fun actionPerformed(p0: AnActionEvent) {
              showMessageRequestFutures.put(MessageActionItem(it.title))
            }
          }
        }
        .toSet()
        .toTypedArray()

    val notification =
      SnykBalloonNotificationHelper.showInfo(requestParams.message, project, *actions)
    val messageActionItem = showMessageRequestFutures.poll(10, TimeUnit.SECONDS)
    notification.expire()
    return CompletableFuture.completedFuture(messageActionItem ?: MessageActionItem(""))
  }

  override fun logMessage(message: MessageParams?) {
    message?.let {
      val logMessage = "[${project.name}] ${message.message}"
      when (it.type) {
        MessageType.Error -> logger.error(logMessage)
        MessageType.Warning -> logger.warn(logMessage)
        MessageType.Info -> logger.info(logMessage)
        MessageType.Log -> logger.debug(logMessage)
        null -> logger.info(logMessage)
      }
    }
  }

  companion object {
    // we only allow one message request at a time
    val showMessageRequestFutures = ArrayBlockingQueue<MessageActionItem>(1)
  }

  override fun dispose() {
    disposed = true
  }

  init {
    Disposer.register(SnykPluginDisposable.getInstance(), this)
  }

  /**
   * Intercept window/showDocument messages from LS so that we can handle AI fix actions within the
   * IDE.
   */
  override fun showDocument(param: ShowDocumentParams): CompletableFuture<ShowDocumentResult> {
    if (disposed) return CompletableFuture.completedFuture(ShowDocumentResult(false))

    val uri = URI.create(param.uri)
    logger.debug("showDocument called with URI: ${param.uri}, scheme=${uri.scheme}")

    return if (uri.scheme == "snyk" && uri.getDecodedParam("action") == SHOW_DETAIL_ACTION) {
      val productType =
        when (LsProduct.getFor(uri.getDecodedParam("product") ?: "")) {
          LsProduct.Code -> ProductType.CODE_SECURITY
          LsProduct.OpenSource -> ProductType.OSS
          LsProduct.InfrastructureAsCode -> ProductType.IAC
          else -> null
        }

      // Track whether we have successfully sent any notifications
      var success = false
      if (productType != null) {
        uri.queryParameters["issueId"]?.let { issueId ->
          val aiFixParams = AiFixParams(issueId, productType)
          logger.debug(
            "Publishing show issue detail notification for issue $issueId, product=$productType"
          )
          publishAsync(project, SnykShowIssueDetailListener.SHOW_ISSUE_DETAIL_TOPIC) {
            onShowIssueDetail(aiFixParams)
          }
          success = true
        } ?: run { logger.debug("Received showDocument URI with no issueID: $uri") }
      } else {
        logger.info("Received showDocument URI with unknown product: $uri")
      }
      CompletableFuture.completedFuture(ShowDocumentResult(success))
    } else if (uri.scheme == "file") {
      logger.debug("showDocument: navigating to file URI: ${param.uri}")
      try {
        val virtualFile = param.uri.toVirtualFileOrNull()
        if (virtualFile != null && virtualFile.isValid) {
          val selection = param.selection
          if (selection != null) {
            val document = virtualFile.getDocument()
            if (document != null) {
              val startOffset =
                document.getSafeOffset(selection.start.line, selection.start.character)
              val endOffset = document.getSafeOffset(selection.end.line, selection.end.character)
              navigateToSource(project, virtualFile, startOffset, endOffset)
              CompletableFuture.completedFuture(ShowDocumentResult(true))
            } else {
              logger.warn("showDocument: could not get document for ${param.uri}")
              CompletableFuture.completedFuture(ShowDocumentResult(false))
            }
          } else {
            navigateToSource(project, virtualFile, 0)
            CompletableFuture.completedFuture(ShowDocumentResult(true))
          }
        } else {
          logger.warn("showDocument: file not found: ${param.uri}")
          CompletableFuture.completedFuture(ShowDocumentResult(false))
        }
      } catch (e: Exception) {
        logger.warn("showDocument: error navigating to ${param.uri}", e)
        CompletableFuture.completedFuture(ShowDocumentResult(false))
      }
    } else if (uri.scheme == "http" || uri.scheme == "https") {
      logger.debug("showDocument: opening external URL: ${param.uri}")
      try {
        BrowserUtil.browse(param.uri)
        CompletableFuture.completedFuture(ShowDocumentResult(true))
      } catch (e: Exception) {
        logger.warn("showDocument: error opening URL: ${param.uri}", e)
        CompletableFuture.completedFuture(ShowDocumentResult(false))
      }
    } else {
      logger.warn("showDocument: unsupported URI scheme '${uri.scheme}': ${param.uri}")
      try {
        return super.showDocument(param)
      } catch (e: UnsupportedOperationException) {
        CompletableFuture.completedFuture(ShowDocumentResult(false))
      }
    }
  }
}
