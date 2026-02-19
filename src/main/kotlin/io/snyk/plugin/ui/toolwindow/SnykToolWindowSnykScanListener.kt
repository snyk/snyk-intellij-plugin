package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.Severity
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.ui.expandTreeNodeRecursively
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_SECURITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.IAC_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.NODE_NOT_SUPPORTED_STATE
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.NO_OSS_FILES
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.OSS_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.SCANNING_TEXT
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.InfoTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykFileTreeNode
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import snyk.common.ProductType
import snyk.common.SnykFileIssueComparator
import snyk.common.lsp.FilterableIssueType
import snyk.common.lsp.LsProduct
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams

private const val CONGRATS_NO_ISSUES_FOUND = "✅ Congrats! No issues found!"
private const val CONGRATS_NO_OPEN_ISSUES_FOUND = "✅ Congrats! No open issues found!"
private const val OPEN_ISSUES_ARE_DISABLED = "Open issues are disabled!"
private const val NO_IGNORED_ISSUES = "✋ No ignored issues, open issues are disabled"
private const val OPEN_AND_IGNORED_ISSUES_ARE_DISABLED = "Open and Ignored issues are disabled!"
private const val NO_FIXABLE_ISSUES = "There are no issues automatically fixable."
private const val IGNORED_ISSUES_FILTERED_BUT_AVAILABLE =
  "Adjust your settings to view Ignored issues."
private const val OPEN_ISSUES_FILTERED_BUT_AVAILABLE = "Adjust your settings to view Open issues."

class SnykToolWindowSnykScanListener(
  val project: Project,
  private val snykToolWindowPanel: SnykToolWindowPanel,
  private val vulnerabilitiesTree: JTree,
  private val rootSecurityIssuesTreeNode: DefaultMutableTreeNode,
  private val rootOssIssuesTreeNode: DefaultMutableTreeNode,
  private val rootIacIssuesTreeNode: DefaultMutableTreeNode,
) : SnykScanListener, Disposable {
  private var disposed = false
    get() {
      return project.isDisposed || ApplicationManager.getApplication().isDisposed || field
    }

  init {
    Disposer.register(SnykPluginDisposable.getInstance(project), this)
  }

  override fun dispose() {
    disposed = true
  }

  fun isDisposed() = disposed

  override fun scanningStarted(snykScan: SnykScanParams) {
    if (disposed) return
    invokeLater {
      if (disposed || project.isDisposed) return@invokeLater
      val cache = getSnykCachedResults(project)
      when (LsProduct.getFor(snykScan.product)) {
        LsProduct.OpenSource -> {
          cache?.currentOSSResultsLS?.clear()
          cache?.currentOssError = null
          (rootOssIssuesTreeNode as? RootOssTreeNode)?.originalCliErrorMessage = null
          removeChildrenAndRefresh(rootOssIssuesTreeNode)
        }
        LsProduct.Code -> {
          cache?.currentSnykCodeResultsLS?.clear()
          cache?.currentSnykCodeError = null
          removeChildrenAndRefresh(rootSecurityIssuesTreeNode)
        }
        LsProduct.InfrastructureAsCode -> {
          cache?.currentIacResultsLS?.clear()
          cache?.currentIacError = null
          removeChildrenAndRefresh(rootIacIssuesTreeNode)
        }
        LsProduct.Unknown -> Unit
      }
      this.snykToolWindowPanel.updateTreeRootNodesPresentation()
      this.snykToolWindowPanel.displayScanningMessage()
    }
  }

  override fun scanningSnykCodeFinished() {
    if (disposed) return
    invokeLater {
      if (disposed || project.isDisposed) return@invokeLater
      // Fetch results inside invokeLater to get the latest cache state
      // (diagnostics may continue arriving after scan finishes)
      val results = getSnykCachedResults(project)?.currentSnykCodeResultsLS ?: emptyMap()
      this.rootSecurityIssuesTreeNode.userObject = "$CODE_SECURITY_ROOT_TEXT (scanning finished)"
      this.snykToolWindowPanel.triggerSelectionListeners = false
      displaySnykCodeResults(results)
      this.snykToolWindowPanel.triggerSelectionListeners = true
    }
    refreshAnnotationsForOpenFiles(project)
  }

  override fun scanningOssFinished() {
    if (disposed) return
    invokeLater {
      if (disposed || project.isDisposed) return@invokeLater
      // Fetch results inside invokeLater to get the latest cache state
      // (diagnostics may continue arriving after scan finishes)
      val results = getSnykCachedResults(project)?.currentOSSResultsLS ?: emptyMap()
      this.rootOssIssuesTreeNode.userObject = "$OSS_ROOT_TEXT (scanning finished)"
      this.snykToolWindowPanel.triggerSelectionListeners = false
      displayOssResults(results)
      this.snykToolWindowPanel.triggerSelectionListeners = true
    }
    refreshAnnotationsForOpenFiles(project)
  }

  override fun scanningIacFinished() {
    if (disposed) return
    invokeLater {
      if (disposed || project.isDisposed) return@invokeLater
      // Fetch results inside invokeLater to get the latest cache state
      // (diagnostics may continue arriving after scan finishes)
      val results = getSnykCachedResults(project)?.currentIacResultsLS ?: emptyMap()
      this.rootIacIssuesTreeNode.userObject = "$IAC_ROOT_TEXT (scanning finished)"
      this.snykToolWindowPanel.triggerSelectionListeners = false
      displayIacResults(results)
      this.snykToolWindowPanel.triggerSelectionListeners = true
    }
    refreshAnnotationsForOpenFiles(project)
  }

  override fun scanningError(snykScan: SnykScanParams) {
    if (disposed) return
    ApplicationManager.getApplication().invokeLater {
      when (LsProduct.getFor(snykScan.product)) {
        LsProduct.OpenSource -> {
          removeChildrenAndRefresh(rootOssIssuesTreeNode)
        }
        LsProduct.Code -> {
          removeChildrenAndRefresh(rootSecurityIssuesTreeNode)
        }
        LsProduct.InfrastructureAsCode -> {
          removeChildrenAndRefresh(rootIacIssuesTreeNode)
        }
        LsProduct.Unknown -> Unit
      }
      snykToolWindowPanel.updateTreeRootNodesPresentation()
      refreshAnnotationsForOpenFiles(project)
    }
  }

  private fun removeChildrenAndRefresh(node: DefaultMutableTreeNode) {
    node.removeAllChildren()
    invokeLater { (vulnerabilitiesTree.model as DefaultTreeModel).nodeStructureChanged(node) }
  }

  override fun onPublishDiagnostics(
    product: LsProduct,
    snykFile: SnykFile,
    issues: Set<ScanIssue>,
  ) {}

  fun displaySnykCodeResults(snykResults: Map<SnykFile, Set<ScanIssue>>) {
    if (disposed) return

    val settings = pluginSettings()

    displayIssues(
      filterableIssueType = ScanIssue.CODE_SECURITY,
      enabledInSettings = settings.snykCodeSecurityIssuesScanEnable,
      filterTree = settings.treeFiltering.codeSecurityResults,
      snykResults = snykResults,
      rootNode = this.rootSecurityIssuesTreeNode,
      securityIssuesCount = snykResults.values.flatten().distinct().size,
    )
  }

  private fun displayResults(
    snykResults: Map<SnykFile, Set<ScanIssue>>,
    enabledInSettings: Boolean,
    filterTree: Boolean,
    rootNode: DefaultMutableTreeNode,
    issueType: String,
  ) {
    if (disposed) return

    val flattenedResults = snykResults.values.flatten()

    when (issueType) {
      ScanIssue.OPEN_SOURCE -> {
        val ossResultsCount =
          flattenedResults
            .filter { it.filterableIssueType == ScanIssue.OPEN_SOURCE }
            .distinct()
            .size
        displayIssues(
          filterableIssueType = ScanIssue.OPEN_SOURCE,
          enabledInSettings = enabledInSettings,
          filterTree = filterTree,
          snykResults = snykResults,
          rootNode = rootNode,
          ossResultsCount = ossResultsCount,
        )
      }
      ScanIssue.INFRASTRUCTURE_AS_CODE -> {
        val iacResultsCount =
          flattenedResults
            .filter { it.filterableIssueType == ScanIssue.INFRASTRUCTURE_AS_CODE }
            .distinct()
            .size
        displayIssues(
          filterableIssueType = ScanIssue.INFRASTRUCTURE_AS_CODE,
          enabledInSettings = enabledInSettings,
          filterTree = filterTree,
          snykResults = snykResults,
          rootNode = rootNode,
          iacResultsCount = iacResultsCount,
        )
      }
    }
  }

  fun displayOssResults(snykResults: Map<SnykFile, Set<ScanIssue>>) {
    if (disposed) return

    val settings = pluginSettings()

    displayResults(
      snykResults,
      settings.ossScanEnable,
      settings.treeFiltering.ossResults,
      this.rootOssIssuesTreeNode,
      ScanIssue.OPEN_SOURCE,
    )
  }

  fun displayIacResults(snykResults: Map<SnykFile, Set<ScanIssue>>) {
    if (disposed) return

    val settings = pluginSettings()
    displayResults(
      snykResults,
      settings.iacScanEnabled,
      settings.treeFiltering.iacResults,
      this.rootIacIssuesTreeNode,
      ScanIssue.INFRASTRUCTURE_AS_CODE,
    )
  }

  private fun displayIssues(
    filterableIssueType: FilterableIssueType,
    enabledInSettings: Boolean,
    filterTree: Boolean,
    snykResults: Map<SnykFile, Set<ScanIssue>>,
    rootNode: DefaultMutableTreeNode,
    ossResultsCount: Int? = null,
    securityIssuesCount: Int? = null,
    iacResultsCount: Int? = null,
  ) {
    val settings = pluginSettings()

    if (settings.token.isNullOrEmpty()) {
      snykToolWindowPanel.displayAuthPanel()
      return
    }

    rootNode.removeAllChildren()

    var rootNodePostFix = ""
    var filteredOssResultsCount = ossResultsCount
    var filteredSecurityIssuesCount = securityIssuesCount
    var filteredIacResultsCount = iacResultsCount

    if (enabledInSettings) {
      // Calculate filtered results first - apply severity filtering if enabled
      val resultsToDisplay =
        if (filterTree) {
          snykResults
            .map { entry ->
              entry.key to
                entry.value.filter {
                  settings.hasSeverityEnabledAndFiltered(it.getSeverityAsEnum())
                }
            }
            .toMap()
        } else {
          snykResults.map { entry -> entry.key to entry.value.toList() }.toMap()
        }

      // Use filtered results for all counts and display
      val filteredIssues = resultsToDisplay.values.flatten().distinct()

      // Update counts to reflect filtered results
      var showCritical = true
      when (filterableIssueType) {
        ScanIssue.OPEN_SOURCE -> filteredOssResultsCount = filteredIssues.size
        ScanIssue.CODE_SECURITY -> {
          filteredSecurityIssuesCount = filteredIssues.size
          showCritical = false
        }
        ScanIssue.INFRASTRUCTURE_AS_CODE -> filteredIacResultsCount = filteredIssues.size
      }

      rootNodePostFix = buildSeveritiesPostfixForFileNode(filteredIssues, showCritical)

      val filteredFixableCount =
        when (filterableIssueType) {
          ScanIssue.CODE_SECURITY -> filteredIssues.count { it.hasAIFix() }
          else -> filteredIssues.count { it.additionalData.isUpgradable }
        }

      addInfoTreeNodes(
        filterableIssueType = filterableIssueType,
        rootNode = rootNode,
        issues = filteredIssues,
        fixableIssuesCount = filteredFixableCount,
      )

      displayResultsForRootTreeNode(rootNode, resultsToDisplay)
    }

    val currentOssError = getSnykCachedResults(project)?.currentOssError
    val cliErrorMessage = currentOssError?.error

    var ossResultsCountForDisplay = filteredOssResultsCount
    if (cliErrorMessage?.contains(NO_OSS_FILES) == true) {
      snykToolWindowPanel.getRootOssIssuesTreeNode().originalCliErrorMessage = cliErrorMessage
      ossResultsCountForDisplay = NODE_NOT_SUPPORTED_STATE
    }

    snykToolWindowPanel.updateTreeRootNodesPresentation(
      securityIssuesCount = filteredSecurityIssuesCount,
      ossResultsCount = ossResultsCountForDisplay,
      iacResultsCount = filteredIacResultsCount,
      addHMLPostfix = rootNodePostFix,
    )

    snykToolWindowPanel.smartReloadRootNode(rootNode)
  }

  private fun getIssueFoundText(issuesCount: Int): String {
    if (pluginSettings().isGlobalIgnoresFeatureEnabled && !pluginSettings().openIssuesEnabled) {
      return OPEN_ISSUES_ARE_DISABLED
    }

    return if (issuesCount == 0) {
      CONGRATS_NO_ISSUES_FOUND
    } else {
      "✋ $issuesCount issue${if (issuesCount == 1) "" else "s"}"
    }
  }

  private fun getIssueFoundTextForCode(
    totalIssuesCount: Int,
    openIssuesCount: Int,
    ignoredIssuesCount: Int,
  ): String {
    if (!pluginSettings().isGlobalIgnoresFeatureEnabled) {
      return getIssueFoundText(totalIssuesCount)
    }

    val showingOpen = pluginSettings().openIssuesEnabled
    val showingIgnored = pluginSettings().ignoredIssuesEnabled

    val openIssuesText = "$openIssuesCount open issue${if (openIssuesCount == 1) "" else "s"}"
    val ignoredIssuesText =
      "$ignoredIssuesCount ignored issue${if (ignoredIssuesCount == 1) "" else "s"}"

    if (showingOpen && showingIgnored) {
      return if (totalIssuesCount == 0) {
        CONGRATS_NO_ISSUES_FOUND
      } else if (ignoredIssuesCount == 0) {
        "✋ $openIssuesText"
      } else {
        "✋ $openIssuesText & $ignoredIssuesText"
      }
    }
    if (showingOpen) {
      return if (openIssuesCount == 0) {
        CONGRATS_NO_OPEN_ISSUES_FOUND
      } else {
        "✋ $openIssuesText"
      }
    }
    if (showingIgnored) {
      return if (ignoredIssuesCount == 0) {
        NO_IGNORED_ISSUES
      } else {
        "✋ $ignoredIssuesText, open issues are disabled"
      }
    }
    return OPEN_AND_IGNORED_ISSUES_ARE_DISABLED // In theory, this is prevented by IntelliJ
  }

  private fun getNoIssueViewOptionsSelectedTreeNode(): InfoTreeNode? {
    if (!pluginSettings().isGlobalIgnoresFeatureEnabled) {
      return null
    }

    if (!pluginSettings().openIssuesEnabled) {
      return InfoTreeNode(OPEN_ISSUES_FILTERED_BUT_AVAILABLE, project)
    }

    return null
  }

  private fun getNoIssueViewOptionsSelectedTreeNodeForCode(): InfoTreeNode? {
    if (!pluginSettings().isGlobalIgnoresFeatureEnabled) {
      return null
    }

    if (!pluginSettings().openIssuesEnabled) {
      return InfoTreeNode(OPEN_ISSUES_FILTERED_BUT_AVAILABLE, project)
    }

    if (!pluginSettings().ignoredIssuesEnabled) {
      return InfoTreeNode(IGNORED_ISSUES_FILTERED_BUT_AVAILABLE, project)
    }

    return null
  }

  private fun getFixableIssuesText(
    fixableIssuesCount: Int,
    sayOpenIssues: Boolean = false,
  ): String =
    if (fixableIssuesCount > 0) {
      "⚡ $fixableIssuesCount${if (sayOpenIssues) " open" else ""} issue${if (fixableIssuesCount == 1) " is" else "s are"} fixable automatically."
    } else {
      NO_FIXABLE_ISSUES
    }

  private fun getFixableIssuesTextForCode(fixableIssuesCount: Int): String? {
    if (pluginSettings().isGlobalIgnoresFeatureEnabled && !pluginSettings().openIssuesEnabled) {
      return null
    }
    return getFixableIssuesText(fixableIssuesCount, pluginSettings().isGlobalIgnoresFeatureEnabled)
  }

  @Suppress("RedundantVisibilityModifierRule")
  fun addInfoTreeNodes(
    filterableIssueType: FilterableIssueType,
    rootNode: DefaultMutableTreeNode,
    issues: List<ScanIssue>,
    fixableIssuesCount: Int? = null,
  ) {
    if (disposed) return
    if (rootNode.userObject == SCANNING_TEXT) {
      return
    }

    // totalIssuesCount is the number of issues returned by LS, which pre-filters on Issue View
    // Options and Severity Filters (to be implemented at time of this comment - 6th May 2025).
    val totalIssuesCount = issues.size
    val ignoredIssuesCount = issues.count { it.isIgnored() }
    // Depending on Issue View Options, ignored issues might be pre-filtered by the LS and so
    // ignoredIssuesCount may be 0.
    // In this case, openIssuesCount is the total issue count returned by the LS.
    val openIssuesCount = totalIssuesCount - ignoredIssuesCount
    val isCodeNode = filterableIssueType == ScanIssue.CODE_SECURITY

    val text =
      if (!isCodeNode) {
        getIssueFoundText(totalIssuesCount)
      } else {
        getIssueFoundTextForCode(totalIssuesCount, openIssuesCount, ignoredIssuesCount)
      }
    rootNode.add(InfoTreeNode(text, project))
    if (totalIssuesCount == 0) {
      val ivoNode =
        if (!isCodeNode) {
          getNoIssueViewOptionsSelectedTreeNode()
        } else {
          getNoIssueViewOptionsSelectedTreeNodeForCode()
        }
      if (ivoNode != null) {
        rootNode.add(ivoNode)
      }
    } else if (fixableIssuesCount != null) {
      val fixableText =
        if (!isCodeNode) {
          getFixableIssuesText(fixableIssuesCount)
        } else {
          getFixableIssuesTextForCode(fixableIssuesCount)
        }
      if (fixableText != null) {
        rootNode.add(InfoTreeNode(fixableText, project))
      }
    }
  }

  private fun displayResultsForRootTreeNode(
    rootNode: DefaultMutableTreeNode,
    issues: Map<SnykFile, List<ScanIssue>>,
  ) {
    fun navigateToSource(virtualFile: VirtualFile, textRange: TextRange): () -> Unit = {
      io.snyk.plugin.navigateToSource(
        project,
        virtualFile,
        textRange.startOffset,
        textRange.endOffset,
      )
    }
    issues
      .toSortedMap(SnykFileIssueComparator(issues))
      .filter { it.value.isNotEmpty() }
      .forEach { entry ->
        val productType =
          when (rootNode) {
            is RootSecurityIssuesTreeNode -> ProductType.CODE_SECURITY
            is RootOssTreeNode -> ProductType.OSS
            is RootIacIssuesTreeNode -> ProductType.IAC
            else -> throw IllegalArgumentException(rootNode.javaClass.simpleName)
          }

        val fileTreeNode = SnykFileTreeNode(entry, productType)
        rootNode.add(fileTreeNode)
        entry.value
          .sortedByDescending { it.priority() }
          .forEach { issue ->
            fileTreeNode.add(
              SuggestionTreeNode(
                project,
                issue,
                navigateToSource(entry.key.virtualFile, issue.textRange ?: TextRange(0, 0)),
              )
            )
          }
      }
    expandTreeNodeRecursively(snykToolWindowPanel.vulnerabilitiesTree, rootNode)
  }

  private fun buildSeveritiesPostfixForFileNode(
    results: List<ScanIssue>,
    showCritical: Boolean = true,
  ): String {
    val critical =
      if (showCritical) {
        "" + results.count { it.getSeverityAsEnum() == Severity.CRITICAL } + " critical, "
      } else {
        ""
      }

    val high = "" + results.count { it.getSeverityAsEnum() == Severity.HIGH } + " high, "
    val medium = "" + results.count { it.getSeverityAsEnum() == Severity.MEDIUM } + " medium, "
    val low = "" + results.count { it.getSeverityAsEnum() == Severity.LOW } + " low"
    return ": $critical$high$medium$low"
  }
}
