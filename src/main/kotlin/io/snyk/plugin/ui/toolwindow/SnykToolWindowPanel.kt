package io.snyk.plugin.ui.toolwindow

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Alarm
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.events.SnykShowIssueDetailListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isHtmlTreeViewEnabled
import io.snyk.plugin.isIacRunning
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.ReferenceChooserDialog
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.ErrorHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootTreeNodeBase
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ChooseBranchNode
import io.snyk.plugin.ui.toolwindow.panels.HtmlTreePanel
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykErrorPanel
import io.snyk.plugin.ui.toolwindow.panels.StatePanel
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import io.snyk.plugin.ui.toolwindow.panels.SummaryPanel
import io.snyk.plugin.ui.toolwindow.panels.TreePanel
import io.snyk.plugin.ui.wrapWithScrollPane
import java.awt.BorderLayout
import java.util.Objects.nonNull
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.runAsync
import snyk.common.ProductType
import snyk.common.SnykError
import snyk.common.lsp.AiFixParams
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.LsProduct
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams
import snyk.common.lsp.settings.FolderConfigSettings

/** Main panel for Snyk tool window. */
@Service(Service.Level.PROJECT)
class SnykToolWindowPanel(val project: Project) : JPanel(), Disposable {
  private val descriptionPanel =
    SimpleToolWindowPanel(true, true).apply { name = "descriptionPanel" }
  private val summaryPanel = SimpleToolWindowPanel(true, true).apply { name = "summaryPanel" }
  private var summaryPanelContent: SummaryPanel? = null
  private var htmlTreePanel: HtmlTreePanel? = null
  private val logger = Logger.getInstance(this::class.java)
  private val rootTreeNode = ChooseBranchNode(project = project)
  private val rootOssTreeNode = RootOssTreeNode(project)
  private val rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
  private val rootIacIssuesTreeNode = RootIacIssuesTreeNode(project)

  internal val vulnerabilitiesTree by lazy {
    rootTreeNode.add(rootOssTreeNode)
    rootTreeNode.add(rootSecurityIssuesTreeNode)
    rootTreeNode.add(rootIacIssuesTreeNode)
    Tree(rootTreeNode).apply { this.isRootVisible = pluginSettings().isDeltaFindingsEnabled() }
  }

  private fun getRootNodeText(folderConfig: FolderConfig): String {
    val detail =
      if (folderConfig.referenceFolderPath.isNullOrBlank()) {
        folderConfig.baseBranch
      } else {
        folderConfig.referenceFolderPath
      }
    val path = folderConfig.folderPath.toNioPathOrNull()
    return "Click to choose base branch or reference folder for ${path?.fileName ?: path.toString()}: [ current: $detail ]"
  }

  /**
   * Flag used to recognize not-user-initiated Description panel reload cases for purposes like:
   * - don't navigate to source (in the Editor)
   */
  private var smartReloadMode = false

  var triggerSelectionListeners = true

  // Debouncing for tree reload operations - coalesces rapid updates
  private val reloadAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val pendingReloadNodes: MutableSet<DefaultMutableTreeNode> =
    java.util.concurrent.ConcurrentHashMap.newKeySet()

  // Debouncing for annotation refresh - coalesces per-file diagnostic updates
  private val annotationRefreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private val pendingAnnotationRefreshFiles: MutableSet<VirtualFile> =
    java.util.concurrent.ConcurrentHashMap.newKeySet()

  // Debouncing for tree refresh - coalesces rapid diagnostic updates per product
  private val treeRefreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private val pendingTreeRefreshProducts: MutableSet<LsProduct> =
    java.util.concurrent.ConcurrentHashMap.newKeySet()

  // Reference to scan listener for debounced tree refresh
  private lateinit var scanListenerLS: SnykToolWindowSnykScanListener

  // Tree node expander for progressive, non-blocking expansion
  private val treeNodeExpander by lazy { TreeNodeExpander(vulnerabilitiesTree) { isDisposed } }

  companion object {
    // Debounce delay in milliseconds - coalesces rapid scan updates
    private const val RELOAD_DEBOUNCE_MS = 100

    // Debounce delay for annotation refresh - allows collecting multiple files
    private const val ANNOTATION_REFRESH_DEBOUNCE_MS = 150

    // Debounce delay for tree refresh - allows diagnostics to accumulate before refreshing tree
    private const val TREE_REFRESH_DEBOUNCE_MS = 200

    // Max files to refresh individually before falling back to global refresh
    private const val MAX_INDIVIDUAL_ANNOTATION_REFRESH = 20

    val OSS_ROOT_TEXT = " " + ProductType.OSS.treeName
    val CODE_SECURITY_ROOT_TEXT = " " + ProductType.CODE_SECURITY.treeName
    val IAC_ROOT_TEXT = " " + ProductType.IAC.treeName

    const val SELECT_ISSUE_TEXT = "Select an issue and start improving your project."
    const val SCAN_PROJECT_TEXT = "Scan your project for security vulnerabilities and code issues."
    const val SCANNING_TEXT = "Scanning project for vulnerabilities..."
    const val AUTH_FAILED_TEXT = "Authentication failed. Please check the API token on "
    const val NO_ISSUES_FOUND_TEXT = " - No issues found"
    const val NO_OSS_FILES = "Could not detect supported target files in"
    const val NO_IAC_FILES = "Could not find any valid IaC files"
    const val NO_SUPPORTED_IAC_FILES_FOUND = " - No supported IaC files found"
    const val NO_SUPPORTED_PACKAGE_MANAGER_FOUND = " - No supported package manager found"
    private const val TOOL_WINDOW_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_WINDOW_SPLITTER_PROPORTION"
    private const val TOOL_TREE_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_TREE_SPLITTER_PROPORTION"
    internal const val NODE_INITIAL_STATE = -1
    const val NODE_NOT_SUPPORTED_STATE = -2
  }

  private val treeNodeStub =
    object : RootTreeNodeBase("", project) {
      override fun getSnykError(): SnykError? = null
    }

  init {
    Disposer.register(SnykPluginDisposable.getInstance(project), this)
    val contentRoots = project.getContentRootPaths()
    var rootNodeText = ""
    if (contentRoots.isNotEmpty()) {
      val folderConfig =
        service<FolderConfigSettings>().getFolderConfig(contentRoots.first().toString())
      rootNodeText = getRootNodeText(folderConfig)
    }

    rootTreeNode.info = rootNodeText

    vulnerabilitiesTree.cellRenderer = SnykTreeCellRenderer()
    layout = BorderLayout()

    // convertor interface seems to be still used in TreeSpeedSearch, although it's marked obsolete
    val convertor =
      Convertor<TreePath, String> { TreeSpeedSearch.NODE_PRESENTATION_FUNCTION.apply(it) }
    TreeUIHelper.getInstance().installTreeSpeedSearch(vulnerabilitiesTree, convertor, true)

    createTreeAndDescriptionPanel()
    chooseMainPanelToDisplay()
    updateSummaryPanel()

    vulnerabilitiesTree.selectionModel.addTreeSelectionListener { treeSelectionEvent ->
      runAsync {
        if (isDisposed || project.isDisposed) return@runAsync
        updateDescriptionPanelBySelectedTreeNode(treeSelectionEvent)
      }
    }

    scanListenerLS = run {
      val scanListener =
        SnykToolWindowSnykScanListener(
          project,
          this,
          vulnerabilitiesTree,
          rootSecurityIssuesTreeNode,
          rootOssTreeNode,
          rootIacIssuesTreeNode,
        )
      project.messageBus.connect(this).subscribe(SnykScanListener.SNYK_SCAN_TOPIC, scanListener)
      scanListener
    }

    project.messageBus
      .connect(this)
      .subscribe(
        SnykScanListener.SNYK_SCAN_TOPIC,
        object : SnykScanListener {
          override fun onPublishDiagnostics(
            product: LsProduct,
            snykFile: SnykFile,
            issues: Set<ScanIssue>,
          ) {
            getSnykCachedResults(project)?.let {
              when (product) {
                LsProduct.Code -> it.currentSnykCodeResultsLS[snykFile] = issues
                LsProduct.OpenSource -> it.currentOSSResultsLS[snykFile] = issues
                LsProduct.InfrastructureAsCode -> it.currentIacResultsLS[snykFile] = issues
                LsProduct.Unknown -> Unit
              }
            }
            // Schedule debounced annotation refresh - coalesces rapid per-file updates
            scheduleAnnotationRefresh(snykFile.virtualFile)
            // Schedule debounced tree refresh - coalesces rapid diagnostic updates per product
            scheduleDebouncedTreeRefresh(product)
          }

          override fun scanningSnykCodeFinished() = Unit

          override fun scanningOssFinished() = Unit

          override fun scanningIacFinished() = Unit

          override fun scanningError(snykScan: SnykScanParams) = Unit
        },
      )

    project.messageBus
      .connect(this)
      .subscribe(
        SnykResultsFilteringListener.SNYK_FILTERING_TOPIC,
        object : SnykResultsFilteringListener {
          override fun filtersChanged() {
            // Fetch data off-EDT - handle each product independently
            // so that filtering works even if some products haven't been scanned
            val codeSecurityResultsLS =
              getSnykCachedResultsForProduct(project, ProductType.CODE_SECURITY)
            val ossResultsLS = getSnykCachedResultsForProduct(project, ProductType.OSS)
            val iacResultsLS = getSnykCachedResultsForProduct(project, ProductType.IAC)

            invokeLater {
              if (isDisposed || project.isDisposed) return@invokeLater
              codeSecurityResultsLS?.let { scanListenerLS.displaySnykCodeResults(it) }
              ossResultsLS?.let { scanListenerLS.displayOssResults(it) }
              iacResultsLS?.let { scanListenerLS.displayIacResults(it) }
            }
          }
        },
      )

    ApplicationManager.getApplication()
      .messageBus
      .connect(this)
      .subscribe(
        SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
        object : SnykCliDownloadListener {
          override fun checkCliExistsFinished() =
            ApplicationManager.getApplication().invokeLater { chooseMainPanelToDisplay() }

          override fun cliDownloadStarted() =
            ApplicationManager.getApplication().invokeLater { displayDownloadMessage() }
        },
      )

    project.messageBus
      .connect(this)
      .subscribe(
        SnykSettingsListener.SNYK_SETTINGS_TOPIC,
        object : SnykSettingsListener {
          override fun settingsChanged() =
            ApplicationManager.getApplication().invokeLater { chooseMainPanelToDisplay() }
        },
      )

    project.messageBus
      .connect(this)
      .subscribe(
        SnykTaskQueueListener.TASK_QUEUE_TOPIC,
        object : SnykTaskQueueListener {
          override fun stopped() =
            ApplicationManager.getApplication().invokeLater {
              updateTreeRootNodesPresentation(
                ossResultsCount = NODE_INITIAL_STATE,
                securityIssuesCount = NODE_INITIAL_STATE,
                iacResultsCount = NODE_INITIAL_STATE,
              )
              displayEmptyDescription()
            }
        },
      )

    project.messageBus
      .connect(this)
      .subscribe(
        SnykShowIssueDetailListener.SHOW_ISSUE_DETAIL_TOPIC,
        object : SnykShowIssueDetailListener {
          override fun onShowIssueDetail(aiFixParams: AiFixParams) {
            val issueId = aiFixParams.issueId
            val product = aiFixParams.product
            if (logger.isDebugEnabled) {
              logger.debug("onShowIssueDetail: issueId=$issueId, product=$product")
            }
            getSnykCachedResultsForProduct(project, product)?.let { results ->
              if (logger.isDebugEnabled) {
                val issueCount = results.values.sumOf { it.size }
                logger.debug(
                  "onShowIssueDetail: cache has ${results.size} files, $issueCount issues"
                )
                val sampleIds = results.values.asSequence().flatten().take(5).map { it.id }.toList()
                logger.debug("onShowIssueDetail: first cached IDs: $sampleIds")
              }
              results.values
                .firstNotNullOfOrNull { issues -> issues.firstOrNull { it.id == issueId } }
                ?.let { scanIssue ->
                  if (logger.isDebugEnabled) logger.debug("onShowIssueDetail: found issue $issueId")
                  selectNodeAndDisplayDescription(scanIssue, forceRefresh = true)
                }
                ?: run {
                  if (logger.isDebugEnabled) {
                    logger.debug("Failed to find issue $issueId in $product cache")
                  }
                }
            }
          }
        },
      )

    // Listen for tool window visibility changes to ensure proper repainting
    project.messageBus
      .connect(this)
      .subscribe(
        ToolWindowManagerListener.TOPIC,
        object : ToolWindowManagerListener {
          override fun toolWindowShown(toolWindow: com.intellij.openapi.wm.ToolWindow) {
            logger.debug("toolWindowShown: ${toolWindow.id}")
            if (toolWindow.id == SnykToolWindowFactory.SNYK_TOOL_WINDOW) {
              logger.debug("Snyk tool window shown, calling refreshUI")
              refreshUI()
            }
          }
        },
      )
  }

  private fun updateDescriptionPanelBySelectedTreeNode(treeSelectionEvent: TreeSelectionEvent) {
    val capturedSmartReloadMode = smartReloadMode
    val capturedNavigateToSourceEnabled = triggerSelectionListeners

    val selectionPath = treeSelectionEvent.path
    if (nonNull(selectionPath) && treeSelectionEvent.isAddedPath) {
      val lastPathComponent = selectionPath.lastPathComponent

      if (
        lastPathComponent is ChooseBranchNode &&
          capturedNavigateToSourceEnabled &&
          !capturedSmartReloadMode
      ) {
        invokeLater { ReferenceChooserDialog(project).show() }
      }

      if (
        !capturedSmartReloadMode &&
          capturedNavigateToSourceEnabled &&
          lastPathComponent is NavigatableToSourceTreeNode
      ) {
        lastPathComponent.navigateToSource()
      }
      val selectedNode = lastPathComponent as? DefaultMutableTreeNode ?: return
      val shouldUpdateSuggestionDescriptionPanel =
        if (selectedNode is SuggestionTreeNode) {
          val cache = getSnykCachedResults(project)
          val issue = selectedNode.issue
          val productIssues =
            when (issue.filterableIssueType) {
              ScanIssue.CODE_SECURITY -> cache?.currentSnykCodeResultsLS
              ScanIssue.OPEN_SOURCE -> cache?.currentOSSResultsLS
              ScanIssue.INFRASTRUCTURE_AS_CODE -> cache?.currentIacResultsLS
              else -> {
                null
              }
            }
          productIssues?.values?.any { issues -> issues.any { issue.id == it.id } } == true
        } else {
          false
        }
      invokeLater {
        if (isDisposed || project.isDisposed) return@invokeLater
        when (selectedNode) {
          is DescriptionHolderTreeNode -> {
            if (selectedNode is SuggestionTreeNode) {
              if (shouldUpdateSuggestionDescriptionPanel) {
                val newDescriptionPanel = selectedNode.getDescriptionPanel()
                clearDescriptionPanel()
                descriptionPanel.add(newDescriptionPanel, BorderLayout.CENTER)
              }
            } else {
              clearDescriptionPanel()
              descriptionPanel.add(selectedNode.getDescriptionPanel(), BorderLayout.CENTER)
            }
          }
          is ErrorHolderTreeNode -> {
            clearDescriptionPanel()
            selectedNode.getSnykError()?.let { displaySnykError(it) } ?: displayEmptyDescription()
          }
          else -> {
            clearDescriptionPanel()
            displayEmptyDescription()
          }
        }
        descriptionPanel.revalidate()
        descriptionPanel.repaint()
      }
    } else {
      invokeLater {
        if (isDisposed || project.isDisposed) return@invokeLater
        displayEmptyDescription()
        descriptionPanel.revalidate()
        descriptionPanel.repaint()
      }
    }
  }

  var isDisposed: Boolean = false

  override fun dispose() {
    isDisposed = true
    clearDescriptionPanel()
  }

  // Throttle for UI refresh to avoid rapid successive refreshes
  private var lastRefreshTime: Long = 0
  private val refreshThrottleMs: Long = 500

  /**
   * Refreshes the tree UI when the tool window becomes visible or the application regains focus.
   * This ensures that any model updates that occurred while the tool window was hidden are properly
   * displayed.
   *
   * Note: Only refreshes the JTree component. JCEF-based panels (summaryPanel, descriptionPanel)
   * manage their own rendering and should not be manually repainted to avoid EDT blocking.
   */
  fun refreshUI() {
    logger.debug("refreshUI called, isDisposed=$isDisposed, projectDisposed=${project.isDisposed}")
    if (isDisposed || project.isDisposed) return

    // Throttle rapid refreshes
    val now = System.currentTimeMillis()
    val timeSinceLastRefresh = now - lastRefreshTime
    if (timeSinceLastRefresh < refreshThrottleMs) {
      logger.debug("refreshUI throttled, timeSinceLastRefresh=$timeSinceLastRefresh ms")
      return
    }
    lastRefreshTime = now

    logger.debug("refreshUI scheduling invokeLater")
    invokeLater {
      logger.debug("refreshUI invokeLater executing")
      if (isDisposed || project.isDisposed) {
        logger.debug("refreshUI: disposed in invokeLater, skipping")
        return@invokeLater
      }
      // Only refresh if the tree is actually showing on screen
      if (!vulnerabilitiesTree.isShowing) {
        logger.debug("refreshUI: tree not showing, skipping")
        return@invokeLater
      }

      // Refresh only the tree view (pure Swing component)
      // JCEF panels manage their own rendering - do not repaint them manually
      logger.debug("refreshUI: revalidating and repainting tree")
      vulnerabilitiesTree.revalidate()
      vulnerabilitiesTree.repaint()
      logger.debug("refreshUI: done")
    }
  }

  /**
   * Schedules a debounced annotation refresh for the given file. Collects files over a short window
   * and then refreshes them in batch.
   */
  private fun scheduleAnnotationRefresh(virtualFile: VirtualFile) {
    pendingAnnotationRefreshFiles.add(virtualFile)
    annotationRefreshAlarm.cancelAllRequests()
    annotationRefreshAlarm.addRequest(
      { flushPendingAnnotationRefreshes() },
      ANNOTATION_REFRESH_DEBOUNCE_MS,
    )
  }

  /** Flushes all pending annotation refreshes, either individually or as a global refresh. */
  private fun flushPendingAnnotationRefreshes() {
    if (isDisposed || project.isDisposed) return

    val filesToRefresh =
      pendingAnnotationRefreshFiles.toList().also { pendingAnnotationRefreshFiles.clear() }

    if (filesToRefresh.isEmpty()) return

    // Invalidate code vision for all affected files
    invokeLater {
      if (!project.isDisposed) {
        project
          .service<CodeVisionHost>()
          .invalidateProvider(CodeVisionHost.LensInvalidateSignal(null))
      }
    }

    // Batch all refreshes into a single invokeLater to avoid EDT queue flooding
    invokeLater {
      if (isDisposed || project.isDisposed) return@invokeLater
      val analyzer = DaemonCodeAnalyzer.getInstance(project)

      if (filesToRefresh.size > MAX_INDIVIDUAL_ANNOTATION_REFRESH) {
        // Too many files - do a global refresh
        analyzer.restart()
      } else {
        // Refresh each file individually within same EDT task
        filesToRefresh.forEach { file ->
          if (file.isValid) {
            PsiManager.getInstance(project).findFile(file)?.let { psiFile ->
              analyzer.restart(psiFile)
            }
          }
        }
      }
    }
  }

  /**
   * Schedules a debounced tree refresh for the given product. Collects products over a short window
   * and then refreshes them in batch. This ensures the tree stays in sync when diagnostics arrive
   * after scan completion.
   */
  private fun scheduleDebouncedTreeRefresh(product: LsProduct) {
    if (product == LsProduct.Unknown) return
    if (htmlTreePanel != null) return
    pendingTreeRefreshProducts.add(product)
    treeRefreshAlarm.cancelAllRequests()
    treeRefreshAlarm.addRequest({ flushPendingTreeRefreshes() }, TREE_REFRESH_DEBOUNCE_MS)
  }

  /** Flushes all pending tree refreshes, updating the tree for each affected product. */
  private fun flushPendingTreeRefreshes() {
    if (isDisposed || project.isDisposed) return

    val productsToRefresh =
      pendingTreeRefreshProducts.toSet().also { pendingTreeRefreshProducts.clear() }

    if (productsToRefresh.isEmpty()) return

    // Refresh tree for each product that received new diagnostics
    invokeLater {
      if (isDisposed || project.isDisposed) return@invokeLater

      vulnerabilitiesTree.isRootVisible = pluginSettings().isDeltaFindingsEnabled()

      productsToRefresh.forEach { product ->
        when (product) {
          LsProduct.OpenSource -> {
            // Skip refresh if scan is still in progress - let scanningOssFinished handle it
            if (isOssRunning(project)) return@forEach
            val results = getSnykCachedResultsForProduct(project, ProductType.OSS)
            if (results != null) {
              scanListenerLS.displayOssResults(results)
            }
          }
          LsProduct.Code -> {
            // Skip refresh if scan is still in progress - let scanningSnykCodeFinished handle it
            if (isSnykCodeRunning(project)) return@forEach
            val results = getSnykCachedResultsForProduct(project, ProductType.CODE_SECURITY)
            if (results != null) {
              scanListenerLS.displaySnykCodeResults(results)
            }
          }
          LsProduct.InfrastructureAsCode -> {
            // Skip refresh if scan is still in progress - let scanningIacFinished handle it
            if (isIacRunning(project)) return@forEach
            val results = getSnykCachedResultsForProduct(project, ProductType.IAC)
            if (results != null) {
              scanListenerLS.displayIacResults(results)
            }
          }
          LsProduct.Unknown -> Unit
        }
      }
    }
  }

  fun cleanUiAndCaches(resetSummaryPanel: Boolean = true) {
    getSnykCachedResults(project)?.clearCaches()
    rootOssTreeNode.originalCliErrorMessage = null
    doCleanUi(true)
    if (resetSummaryPanel) {
      invokeLater {
        if (isDisposed || project.isDisposed) return@invokeLater
        updateSummaryPanel()
      }
    }
    refreshAnnotationsForOpenFiles(project)
  }

  private fun doCleanUi(reDisplayDescription: Boolean) {
    removeAllChildren()
    updateTreeRootNodesPresentation(
      ossResultsCount = NODE_INITIAL_STATE,
      securityIssuesCount = NODE_INITIAL_STATE,
      iacResultsCount = NODE_INITIAL_STATE,
    )

    invokeLater { (vulnerabilitiesTree.model as DefaultTreeModel).reload() }
    htmlTreePanel?.reset()

    if (reDisplayDescription) {
      displayEmptyDescription()
    }
  }

  private fun removeAllChildren(
    rootNodesToUpdate: List<DefaultMutableTreeNode> =
      listOf(rootOssTreeNode, rootSecurityIssuesTreeNode, rootIacIssuesTreeNode)
  ) {
    rootNodesToUpdate.forEach {
      if (it.childCount > 0) {
        it.removeAllChildren()
      }
    }
  }

  internal fun chooseMainPanelToDisplay() {
    val settings = pluginSettings()
    when {
      settings.token.isNullOrEmpty() -> displayAuthPanel()
      settings.pluginFirstRun -> {
        pluginSettings().pluginFirstRun = false
        displayEmptyDescription()
        // Run on background thread to avoid blocking EDT during LS initialization
        runAsync {
          try {
            enableCodeScanAccordingToServerSetting()
          } catch (e: Exception) {
            invokeLater {
              displaySnykError(
                SnykError(e.message ?: "Exception while initializing plugin {${e.message}", "")
              )
            }
            logger.error("Failed to apply Snyk settings", e)
          }
        }
      }
      else -> displayEmptyDescription()
    }
  }

  private fun triggerScan() {
    getSnykTaskQueueService(project)?.scan()
  }

  fun displayAuthPanel() {
    if (isDisposed) return
    doCleanUi(false)
    clearDescriptionPanel()
    val authPanel = SnykAuthPanel(project)
    Disposer.register(this, authPanel)
    descriptionPanel.add(authPanel, BorderLayout.CENTER)
    revalidate()
  }

  private fun enableCodeScanAccordingToServerSetting() {
    pluginSettings().apply {
      try {
        // update settings if we get a valid/correct response, else log the error and do nothing
        val sastSettings = LanguageServerWrapper.getInstance(project).getSastSettings()
        sastOnServerEnabled = sastSettings?.sastEnabled ?: false
        val codeScanAllowed = sastOnServerEnabled == true
        snykCodeSecurityIssuesScanEnable = snykCodeSecurityIssuesScanEnable && codeScanAllowed
      } catch (clientException: RuntimeException) {
        logger.error(clientException)
      }
    }
  }

  private fun createTreeAndDescriptionPanel() {
    removeAll()
    val vulnerabilitiesSplitter = OnePixelSplitter(TOOL_WINDOW_SPLITTER_PROPORTION_KEY, 0.4f)
    add(vulnerabilitiesSplitter, BorderLayout.CENTER)

    val treeSplitter = OnePixelSplitter(true, TOOL_TREE_SPLITTER_PROPORTION_KEY, 0.25f)
    treeSplitter.firstComponent = summaryPanel

    if (isHtmlTreeViewEnabled() && JBCefApp.isSupported()) {
      htmlTreePanel = HtmlTreePanel(project)
      Disposer.register(this, htmlTreePanel!!)
      treeSplitter.secondComponent = htmlTreePanel
    } else {
      treeSplitter.secondComponent = TreePanel(vulnerabilitiesTree)
    }

    vulnerabilitiesSplitter.firstComponent = treeSplitter
    vulnerabilitiesSplitter.secondComponent = descriptionPanel
  }

  private fun displayEmptyDescription() {
    when {
      isCliDownloading() -> displayDownloadMessage()
      pluginSettings().token.isNullOrEmpty() -> displayAuthPanel()
      isScanRunning(project) -> displayScanningMessage()
      noIssuesInAnyProductFound() -> displayNoVulnerabilitiesMessage()
      else -> displaySelectVulnerabilityMessage()
    }
  }

  private fun updateSummaryPanel() {
    this.summaryPanelContent?.let { Disposer.dispose(it) }

    val summaryPanelContent = SummaryPanel(project)
    this.summaryPanelContent = summaryPanelContent
    summaryPanel.removeAll()
    Disposer.register(this, summaryPanelContent)
    summaryPanel.add(summaryPanelContent)
    revalidate()
  }

  private fun noIssuesInAnyProductFound() =
    rootOssTreeNode.childCount == 0 &&
      rootSecurityIssuesTreeNode.childCount == 0 &&
      rootIacIssuesTreeNode.childCount == 0

  /**
   * public only for Tests Params value: `null` - if not qualify for `scanning` or `error` state
   * then do NOT change previous value `NODE_INITIAL_STATE` - initial state (clean all postfixes)
   */
  fun updateTreeRootNodesPresentation(
    ossResultsCount: Int? = null,
    securityIssuesCount: Int? = null,
    iacResultsCount: Int? = null,
    addHMLPostfix: String = "",
  ) {
    val settings = pluginSettings()

    val realOssError =
      getSnykCachedResults(project)?.currentOssError != null &&
        ossResultsCount != NODE_NOT_SUPPORTED_STATE
    val realIacError =
      getSnykCachedResults(project)?.currentIacError != null &&
        iacResultsCount != NODE_NOT_SUPPORTED_STATE

    val newOssTreeNodeText =
      getNewOssTreeNodeText(settings, realOssError, ossResultsCount, addHMLPostfix)
    newOssTreeNodeText?.let { rootOssTreeNode.userObject = it }

    val newSecurityIssuesNodeText =
      getNewSecurityIssuesNodeText(settings, securityIssuesCount, addHMLPostfix)
    newSecurityIssuesNodeText?.let { rootSecurityIssuesTreeNode.userObject = it }

    val newIacTreeNodeText =
      getNewIacTreeNodeText(settings, realIacError, iacResultsCount, addHMLPostfix)
    newIacTreeNodeText?.let { rootIacIssuesTreeNode.userObject = it }

    val newRootTreeNodeText = getNewRootTreeNodeText()
    newRootTreeNodeText.let { rootTreeNode.info = it }
  }

  private fun getNewRootTreeNodeText(): String {
    val contentRoots = project.getContentRootPaths()
    if (contentRoots.isEmpty()) {
      return "No content roots found"
    }
    val folderConfig =
      service<FolderConfigSettings>().getFolderConfig(contentRoots.first().toString())
    return getRootNodeText(folderConfig)
  }

  private fun getNewIacTreeNodeText(
    settings: SnykApplicationSettingsStateService,
    realError: Boolean,
    iacResultsCount: Int?,
    addHMLPostfix: String,
  ) =
    when {
      realError -> {
        val errorSuffix = getSnykCachedResults(project)?.currentIacError?.treeNodeSuffix ?: ""
        "$IAC_ROOT_TEXT $errorSuffix"
      }
      isIacRunning(project) && settings.iacScanEnabled -> "$IAC_ROOT_TEXT (scanning...)"
      else ->
        iacResultsCount?.let { count ->
          IAC_ROOT_TEXT +
            when {
              count == NODE_INITIAL_STATE -> ""
              count == 0 -> NO_ISSUES_FOUND_TEXT
              count > 0 -> ProductType.IAC.getCountText(count, isUniqueCount = true) + addHMLPostfix
              count == NODE_NOT_SUPPORTED_STATE -> NO_SUPPORTED_IAC_FILES_FOUND
              else -> throw IllegalStateException("ResultsCount is not meaningful")
            }
        }
    }

  private fun getNewSecurityIssuesNodeText(
    settings: SnykApplicationSettingsStateService,
    securityIssuesCount: Int?,
    addHMLPostfix: String,
  ) =
    when {
      getSnykCachedResults(project)?.currentSnykCodeError != null -> {
        val errorSuffix = getSnykCachedResults(project)?.currentSnykCodeError?.treeNodeSuffix ?: ""
        "$CODE_SECURITY_ROOT_TEXT $errorSuffix"
      }
      isSnykCodeRunning(project) && settings.snykCodeSecurityIssuesScanEnable ->
        "$CODE_SECURITY_ROOT_TEXT (scanning...)"
      else ->
        securityIssuesCount?.let { count ->
          CODE_SECURITY_ROOT_TEXT +
            when {
              count == NODE_INITIAL_STATE -> ""
              count == 0 -> NO_ISSUES_FOUND_TEXT
              count > 0 -> ProductType.CODE_SECURITY.getCountText(count) + addHMLPostfix
              else -> throw IllegalStateException("ResultsCount is not meaningful")
            }
        }
    }

  private fun getNewOssTreeNodeText(
    settings: SnykApplicationSettingsStateService,
    realError: Boolean,
    ossResultsCount: Int?,
    addHMLPostfix: String,
  ) =
    when {
      realError -> {
        val errorSuffix = getSnykCachedResults(project)?.currentOssError?.treeNodeSuffix ?: ""
        "$OSS_ROOT_TEXT $errorSuffix"
      }
      isOssRunning(project) && settings.ossScanEnable -> "$OSS_ROOT_TEXT (scanning...)"
      else ->
        ossResultsCount?.let { count ->
          OSS_ROOT_TEXT +
            when {
              count == NODE_INITIAL_STATE -> ""
              count == 0 -> {
                NO_ISSUES_FOUND_TEXT
              }
              count > 0 -> ProductType.OSS.getCountText(count, isUniqueCount = true) + addHMLPostfix
              count == NODE_NOT_SUPPORTED_STATE -> NO_SUPPORTED_PACKAGE_MANAGER_FOUND
              else -> throw IllegalStateException("ResultsCount is not meaningful")
            }
        }
    }

  private fun displayNoVulnerabilitiesMessage() {
    clearDescriptionPanel()

    val selectedTreeNode =
      vulnerabilitiesTree.selectionPath?.lastPathComponent as? RootTreeNodeBase ?: treeNodeStub
    val messageHtmlText = selectedTreeNode.getNoVulnerabilitiesMessage()

    val emptyStatePanel = StatePanel(messageHtmlText, "Run Scan") { triggerScan() }

    descriptionPanel.add(wrapWithScrollPane(emptyStatePanel), BorderLayout.CENTER)
    revalidate()
  }

  fun displayScanningMessage() {
    clearDescriptionPanel()

    val selectedTreeNode =
      vulnerabilitiesTree.selectionPath?.lastPathComponent as? RootTreeNodeBase ?: treeNodeStub
    val messageHtmlText = selectedTreeNode.getScanningMessage()

    val statePanel =
      StatePanel(messageHtmlText, "Stop Scanning") { getSnykTaskQueueService(project)?.stopScan() }

    descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)

    revalidate()
  }

  private fun displayDownloadMessage() {
    clearDescriptionPanel()

    val statePanel =
      StatePanel("Downloading Snyk CLI...", "Stop Downloading") {
        getSnykCliDownloaderService().stopCliDownload()
        displayEmptyDescription()
      }

    descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)

    revalidate()
  }

  internal fun userObjectsForExpandedNodes(rootNode: DefaultMutableTreeNode) =
    if (rootNode.childCount == 0) {
      null
    } else {
      TreeUtil.collectExpandedUserObjects(vulnerabilitiesTree, TreePath(rootNode.path))
    }

  private fun displaySelectVulnerabilityMessage() {
    val scrollPanelCandidate = descriptionPanel.components.firstOrNull()
    if (
      scrollPanelCandidate is JScrollPane &&
        scrollPanelCandidate.components.firstOrNull() is IssueDescriptionPanel
    ) {
      // vulnerability/suggestion already selected
      return
    }
    clearDescriptionPanel()

    val selectedTreeNode =
      vulnerabilitiesTree.selectionPath?.lastPathComponent as? RootTreeNodeBase ?: treeNodeStub
    val messageHtmlText = selectedTreeNode.getSelectVulnerabilityMessage()
    val statePanel = StatePanel(messageHtmlText)

    descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
    revalidate()
  }

  private fun displaySnykError(snykError: SnykError) {
    clearDescriptionPanel()

    descriptionPanel.add(SnykErrorPanel(snykError), BorderLayout.CENTER)

    revalidate()
  }

  /**
   * Re-expand previously expanded children (if `null` then expand All children) Keep selection in
   * the Tree (if any) Uses debouncing to coalesce rapid updates and chunked expansion to avoid
   * blocking EDT.
   */
  internal fun smartReloadRootNode(nodeToReload: DefaultMutableTreeNode) {
    // Only show empty description if no issue is currently selected
    // This preserves the issue details panel when new scan results arrive
    val currentlySelectedIssue =
      (vulnerabilitiesTree.selectionPath?.lastPathComponent as? SuggestionTreeNode)?.issue
    if (currentlySelectedIssue == null) {
      displayEmptyDescription()
    }

    // Debounce: add node to pending set and schedule reload
    // This allows multiple product nodes to be collected and reloaded together
    pendingReloadNodes.add(nodeToReload)
    reloadAlarm.cancelAllRequests()
    reloadAlarm.addRequest(
      {
        val nodesToReload = pendingReloadNodes.toList()
        pendingReloadNodes.clear()
        nodesToReload.forEach { doSmartReload(it) }
      },
      RELOAD_DEBOUNCE_MS,
    )
  }

  /** Performs the actual tree reload with chunked expansion to minimize EDT blocking. */
  private fun doSmartReload(nodeToReload: DefaultMutableTreeNode) {
    if (isDisposed) return

    // Gather current tree state on EDT (must be done before reload)
    val userObjectsForExpandedChildren = userObjectsForExpandedNodes(nodeToReload)

    // Save the currently selected issue ID (if any) to restore after reload
    val selectedPath = vulnerabilitiesTree.selectionPath
    val selectedIssueId = (selectedPath?.lastPathComponent as? SuggestionTreeNode)?.issue?.id

    // Reload the tree model (fast operation)
    (vulnerabilitiesTree.model as DefaultTreeModel).reload(nodeToReload)

    // Expand nodes progressively to keep UI responsive
    treeNodeExpander.expandProgressively(nodeToReload, userObjectsForExpandedChildren) {
      // After expansion completes, restore selection by finding node with same issue ID
      restoreSelectionByIssueId(selectedIssueId)
    }
  }

  /**
   * Restores tree selection after reload/expansion by finding node with matching issue ID. This
   * handles the case where node objects are recreated during tree reload.
   */
  private fun restoreSelectionByIssueId(issueId: String?) {
    if (issueId == null) return

    smartReloadMode = true
    try {
      val nodeToSelect =
        TreeUtil.findNode(rootTreeNode) { node ->
          (node as? SuggestionTreeNode)?.issue?.id == issueId
        }
      nodeToSelect?.let { TreeUtil.selectNode(vulnerabilitiesTree, it) }
    } finally {
      smartReloadMode = false
    }
  }

  private fun selectAndDisplayNodeWithIssueDescription(
    selectCondition: (DefaultMutableTreeNode) -> Boolean,
    forceRefresh: Boolean = false,
  ) {
    val node = TreeUtil.findNode(rootTreeNode) { selectCondition(it) }
    if (node != null) {
      invokeLater {
        try {
          if (forceRefresh) {
            vulnerabilitiesTree.clearSelection()
          } else {
            triggerSelectionListeners = false
          }
          TreeUtil.selectNode(vulnerabilitiesTree, node)
        } finally {
          triggerSelectionListeners = true
        }
      }
    }
  }

  fun selectNodeAndDisplayDescription(scanIssue: ScanIssue, forceRefresh: Boolean) {
    if (isHtmlTreeViewEnabled()) {
      selectNodeInHtmlTreeAndShowDescription(scanIssue)
    } else {
      selectAndDisplayNodeWithIssueDescription(
        { treeNode ->
          treeNode is SuggestionTreeNode && (treeNode.userObject as ScanIssue).id == scanIssue.id
        },
        forceRefresh,
      )
    }
  }

  private fun selectNodeInHtmlTreeAndShowDescription(scanIssue: ScanIssue) {
    htmlTreePanel?.selectNode(scanIssue.id)
    invokeLater {
      if (isDisposed || project.isDisposed) return@invokeLater
      clearDescriptionPanel()
      descriptionPanel.add(SuggestionDescriptionPanel(project, scanIssue), BorderLayout.CENTER)
      descriptionPanel.revalidate()
      descriptionPanel.repaint()
    }
  }

  private fun clearDescriptionPanel() {
    for (child in descriptionPanel.components) {
      if (child is Disposable) {
        Disposer.dispose(child)
      }
    }
    descriptionPanel.removeAll()
  }

  @TestOnly fun getRootIacIssuesTreeNode() = rootIacIssuesTreeNode

  @TestOnly fun getRootOssIssuesTreeNode() = rootOssTreeNode

  @TestOnly fun getRootSecurityIssuesTreeNode() = rootSecurityIssuesTreeNode

  fun getTree() = vulnerabilitiesTree

  @TestOnly fun getRootNode() = rootTreeNode

  @TestOnly fun getDescriptionPanel() = descriptionPanel

  @TestOnly
  fun setHtmlTreePanelForTest(panel: HtmlTreePanel?) {
    htmlTreePanel = panel
  }

  @TestOnly
  fun scheduleDebouncedTreeRefreshForTest(product: LsProduct) =
    scheduleDebouncedTreeRefresh(product)
}
