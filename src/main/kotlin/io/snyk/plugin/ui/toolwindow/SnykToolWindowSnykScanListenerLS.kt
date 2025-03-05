package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.ui.expandTreeNodeRecursively
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_QUALITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_SECURITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.IAC_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.NODE_NOT_SUPPORTED_STATE
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.NO_OSS_FILES
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.OSS_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.SCANNING_TEXT
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootContainerIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.InfoTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykFileTreeNode
import snyk.common.ProductType
import snyk.common.SnykFileIssueComparator
import snyk.common.lsp.LsProduct
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class SnykToolWindowSnykScanListenerLS(
    val project: Project,
    private val snykToolWindowPanel: SnykToolWindowPanel,
    private val vulnerabilitiesTree: JTree,
    private val rootSecurityIssuesTreeNode: DefaultMutableTreeNode,
    private val rootQualityIssuesTreeNode: DefaultMutableTreeNode,
    private val rootOssIssuesTreeNode: DefaultMutableTreeNode,
    private val rootIacIssuesTreeNode: DefaultMutableTreeNode,
) : SnykScanListenerLS, Disposable {
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
        ApplicationManager.getApplication().invokeLater {
            this.snykToolWindowPanel.cleanUiAndCaches()
            this.snykToolWindowPanel.updateTreeRootNodesPresentation()
            this.snykToolWindowPanel.displayScanningMessage()
        }
    }

    override fun scanningSnykCodeFinished() {
        if (disposed) return
        ApplicationManager.getApplication().invokeLater {
            this.rootSecurityIssuesTreeNode.userObject = "$CODE_SECURITY_ROOT_TEXT (scanning finished)"
            this.rootQualityIssuesTreeNode.userObject = "$CODE_QUALITY_ROOT_TEXT (scanning finished)"
            this.snykToolWindowPanel.triggerSelectionListeners = false
            val snykCachedResults = getSnykCachedResults(project)
            displaySnykCodeResults(snykCachedResults?.currentSnykCodeResultsLS ?: emptyMap())
            this.snykToolWindowPanel.triggerSelectionListeners = true
        }
        refreshAnnotationsForOpenFiles(project)
    }

    override fun scanningOssFinished() {
        if (disposed) return
        ApplicationManager.getApplication().invokeLater {
            this.rootOssIssuesTreeNode.userObject = "$OSS_ROOT_TEXT (scanning finished)"
            this.snykToolWindowPanel.triggerSelectionListeners = false
            val snykCachedResults = getSnykCachedResults(project)
            displayOssResults(snykCachedResults?.currentOSSResultsLS ?: emptyMap())
            this.snykToolWindowPanel.triggerSelectionListeners = true
        }
        refreshAnnotationsForOpenFiles(project)
    }

    override fun scanningIacFinished() {
        if (disposed) return
        ApplicationManager.getApplication().invokeLater {
            this.rootIacIssuesTreeNode.userObject = "$IAC_ROOT_TEXT (scanning finished)"
            this.snykToolWindowPanel.triggerSelectionListeners = false
            val snykCachedResults = getSnykCachedResults(project)
            displayIacResults(snykCachedResults?.currentIacResultsLS ?: emptyMap())
            this.snykToolWindowPanel.triggerSelectionListeners = true
        }
        refreshAnnotationsForOpenFiles(project)
    }

    override fun scanningError(snykScan: SnykScanParams) {
        when (LsProduct.getFor(snykScan.product)) {
            LsProduct.OpenSource -> {
                this.rootOssIssuesTreeNode.removeAllChildren()
                this.rootOssIssuesTreeNode.userObject = "$OSS_ROOT_TEXT (error)"
            }

            LsProduct.Code -> {
                this.rootSecurityIssuesTreeNode.removeAllChildren()
                this.rootSecurityIssuesTreeNode.userObject = "$CODE_SECURITY_ROOT_TEXT (error)"
                this.rootQualityIssuesTreeNode.removeAllChildren()
                this.rootQualityIssuesTreeNode.userObject = "$CODE_QUALITY_ROOT_TEXT (error)"
            }

            LsProduct.InfrastructureAsCode -> {
                this.rootIacIssuesTreeNode.removeAllChildren()
                this.rootIacIssuesTreeNode.userObject = "$IAC_ROOT_TEXT (error)"
            }

            LsProduct.Container -> Unit
            LsProduct.Unknown -> Unit
        }
        refreshAnnotationsForOpenFiles(project)
    }

    override fun onPublishDiagnostics(product: LsProduct, snykFile: SnykFile, issueList: List<ScanIssue>) {}

    fun displaySnykCodeResults(snykResults: Map<SnykFile, List<ScanIssue>>) {
        if (disposed) return
        if (getSnykCachedResults(project)?.currentSnykCodeError != null) return

        val settings = pluginSettings()

        // display Security issues
        val securityIssues =
            snykResults
                .map { it.key to it.value.filter { issue -> issue.additionalData.isSecurityType } }
                .toMap()

        displayIssues(
            enabledInSettings = settings.snykCodeSecurityIssuesScanEnable,
            filterTree = settings.treeFiltering.codeSecurityResults,
            snykResults = securityIssues,
            rootNode = this.rootSecurityIssuesTreeNode,
            securityIssuesCount = securityIssues.values.flatten().distinct().size,
            fixableIssuesCount = securityIssues.values.flatten().distinct().count { it.hasAIFix() },
        )

        // display Quality (non Security) issues
        val qualityIssues =
            snykResults
                .map { it.key to it.value.filter { issue -> !issue.additionalData.isSecurityType } }
                .toMap()

        displayIssues(
            enabledInSettings = settings.snykCodeQualityIssuesScanEnable,
            filterTree = settings.treeFiltering.codeQualityResults,
            snykResults = qualityIssues,
            rootNode = this.rootQualityIssuesTreeNode,
            qualityIssuesCount = qualityIssues.values.flatten().distinct().size,
            fixableIssuesCount = qualityIssues.values.flatten().distinct().count { it.hasAIFix() },
        )
    }

    private fun displayResults(
        snykResults: Map<SnykFile, List<ScanIssue>>,
        enabledInSettings: Boolean,
        filterTree: Boolean,
        rootNode: DefaultMutableTreeNode,
        issueType: String
    ) {
        if (disposed) return
        if (getSnykCachedResults(project)?.currentIacError != null) return // TODO Why only check for IaC error?

        val flattenedResults = snykResults.values.flatten()

        when (issueType) {
            ScanIssue.OPEN_SOURCE -> {
                val ossResultsCount =
                    flattenedResults.filter { it.filterableIssueType == ScanIssue.OPEN_SOURCE }.distinct().size
                displayIssues(
                    enabledInSettings = enabledInSettings,
                    filterTree = filterTree,
                    snykResults = snykResults,
                    rootNode = rootNode,
                    ossResultsCount = ossResultsCount,
                    fixableIssuesCount = flattenedResults.count { it.additionalData.isUpgradable }
                )
            }

            ScanIssue.INFRASTRUCTURE_AS_CODE -> {
                val iacResultsCount =
                    flattenedResults.filter { it.filterableIssueType == ScanIssue.INFRASTRUCTURE_AS_CODE }
                        .distinct().size
                displayIssues(
                    enabledInSettings = enabledInSettings,
                    filterTree = filterTree,
                    snykResults = snykResults,
                    rootNode = rootNode,
                    iacResultsCount = iacResultsCount,
                    fixableIssuesCount = flattenedResults.count { it.additionalData.isUpgradable }
                )
            }
        }
    }

    fun displayOssResults(snykResults: Map<SnykFile, List<ScanIssue>>) {
        if (disposed) return
        if (getSnykCachedResults(project)?.currentOssError != null) return

        val settings = pluginSettings()

        displayResults(
            snykResults,
            settings.ossScanEnable,
            settings.treeFiltering.ossResults,
            this.rootOssIssuesTreeNode,
            ScanIssue.OPEN_SOURCE
        )
    }

    fun displayIacResults(snykResults: Map<SnykFile, List<ScanIssue>>) {
        if (disposed) return
        if (getSnykCachedResults(project)?.currentIacError != null) return

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
        enabledInSettings: Boolean,
        filterTree: Boolean,
        snykResults: Map<SnykFile, List<ScanIssue>>,
        rootNode: DefaultMutableTreeNode,
        ossResultsCount: Int? = null,
        securityIssuesCount: Int? = null,
        qualityIssuesCount: Int? = null,
        iacResultsCount: Int? = null,
        containerResultsCount: Int? = null,
        fixableIssuesCount: Int? = null,
    ) {
        val settings = pluginSettings()

        if (settings.token.isNullOrEmpty()) {
            snykToolWindowPanel.displayAuthPanel()
            return
        }

        val userObjectsForExpandedNodes =
            snykToolWindowPanel.userObjectsForExpandedNodes(rootNode)
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        rootNode.removeAllChildren()

        var rootNodePostFix = ""

        if (enabledInSettings) {
            rootNodePostFix = buildSeveritiesPostfixForFileNode(snykResults)

            if (filterTree) {
                addInfoTreeNodes(
                    rootNode = rootNode,
                    issues = snykResults.values.flatten().distinct(),
                    fixableIssuesCount = fixableIssuesCount,
                )

                var includeIgnoredIssues = true
                var includeOpenedIssues = true
                if (settings.isGlobalIgnoresFeatureEnabled) {
                    includeOpenedIssues = settings.openIssuesEnabled
                    includeIgnoredIssues = settings.ignoredIssuesEnabled
                }

                val resultsToDisplay =
                    snykResults.map { entry ->
                        entry.key to
                            entry.value.filter {
                                settings.hasSeverityEnabledAndFiltered(it.getSeverityAsEnum()) &&
                                    it.isVisible(
                                        includeOpenedIssues,
                                        includeIgnoredIssues,
                                    )
                            }
                    }.toMap()
                displayResultsForRootTreeNode(rootNode, resultsToDisplay)
            }
        }

        val currentOssError = getSnykCachedResults(project)?.currentOssError
        val cliErrorMessage = currentOssError?.message

        var ossResultsCountForDisplay = ossResultsCount
        if (cliErrorMessage?.contains(NO_OSS_FILES) == true) {
            snykToolWindowPanel.getRootOssIssuesTreeNode().originalCliErrorMessage = cliErrorMessage
            ossResultsCountForDisplay = NODE_NOT_SUPPORTED_STATE
        }

        snykToolWindowPanel.updateTreeRootNodesPresentation(
            securityIssuesCount = securityIssuesCount,
            qualityIssuesCount = qualityIssuesCount,
            ossResultsCount = ossResultsCountForDisplay,
            iacResultsCount = iacResultsCount,
            containerResultsCount = containerResultsCount,
            addHMLPostfix = rootNodePostFix,
        )

        snykToolWindowPanel.smartReloadRootNode(
            rootNode,
            userObjectsForExpandedNodes,
            selectedNodeUserObject,
        )
    }

    @Suppress("RedundantVisibilityModifierRule")
    fun addInfoTreeNodes(
        rootNode: DefaultMutableTreeNode,
        issues: List<ScanIssue>,
        fixableIssuesCount: Int? = null,
    ) {
        if (disposed) return
        if (rootNode.userObject == SCANNING_TEXT) {
            return
        }

        val settings = pluginSettings()
        var text = "✅ Congrats! No issues found!"
        val issuesCount = issues.size
        val ignoredIssuesCount = issues.count { it.isIgnored() }
        if (issuesCount != 0) {
            val plural = getPlural(issuesCount)
            text = "✋ $issuesCount issue$plural found by Snyk"
            if (pluginSettings().isGlobalIgnoresFeatureEnabled) {
                text += ", $ignoredIssuesCount ignored"
            }
        }
        rootNode.add(
            InfoTreeNode(
                text,
                project,
            ),
        )

        if (fixableIssuesCount != null) {
            if (fixableIssuesCount > 0) {
                val plural = getPlural(fixableIssuesCount)
                rootNode.add(
                    InfoTreeNode(
                        "⚡ $fixableIssuesCount issue$plural can be fixed automatically",
                        project,
                    ),
                )
            } else {
                rootNode.add(
                    InfoTreeNode("There are no issues automatically fixable", project),
                )
            }
        }
        if (pluginSettings().isGlobalIgnoresFeatureEnabled) {
            if (ignoredIssuesCount == issuesCount && !settings.ignoredIssuesEnabled) {
                rootNode.add(
                    InfoTreeNode(
                        "Adjust your Issue View Options to see ignored issues.",
                        project,
                    ),
                )
            } else if (ignoredIssuesCount == 0 && !settings.openIssuesEnabled) {
                rootNode.add(
                    InfoTreeNode(
                        "Adjust your Issue View Options to open issues.",
                        project,
                    ),
                )
            }
        }
    }

    private fun getPlural(issuesCount: Int) = if (issuesCount > 1) {
        "s"
    } else {
        ""
    }

    private fun displayResultsForRootTreeNode(
        rootNode: DefaultMutableTreeNode,
        issues: Map<SnykFile, List<ScanIssue>>,
    ) {
        fun navigateToSource(
            virtualFile: VirtualFile,
            textRange: TextRange,
        ): () -> Unit =
            {
                io.snyk.plugin.navigateToSource(project, virtualFile, textRange.startOffset, textRange.endOffset)
            }
        issues
            .toSortedMap(SnykFileIssueComparator(issues))
            .filter { it.value.isNotEmpty() }
            .forEach { entry ->
                val productType =
                    when (rootNode) {
                        is RootQualityIssuesTreeNode -> ProductType.CODE_QUALITY
                        is RootSecurityIssuesTreeNode -> ProductType.CODE_SECURITY
                        is RootOssTreeNode -> ProductType.OSS
                        is RootContainerIssuesTreeNode -> ProductType.CONTAINER
                        is RootIacIssuesTreeNode -> ProductType.IAC
                        else -> throw IllegalArgumentException(rootNode.javaClass.simpleName)
                    }

                val fileTreeNode =
                    SnykFileTreeNode(entry, productType)
                rootNode.add(fileTreeNode)
                entry.value.sortedByDescending { it.priority() }
                    .forEach { issue ->
                        fileTreeNode.add(
                            SuggestionTreeNode(
                                project,
                                issue,
                                navigateToSource(entry.key.virtualFile, issue.textRange ?: TextRange(0, 0)),
                            ),
                        )
                    }
            }
        expandTreeNodeRecursively(snykToolWindowPanel.vulnerabilitiesTree, rootNode)
    }

    private fun buildSeveritiesPostfixForFileNode(results: Map<SnykFile, List<ScanIssue>>): String {
        val high = results.values.flatten().count { it.getSeverityAsEnum() == Severity.HIGH }
        val medium = results.values.flatten().count { it.getSeverityAsEnum() == Severity.MEDIUM }
        val low = results.values.flatten().count { it.getSeverityAsEnum() == Severity.LOW }
        return ": $high high, $medium medium, $low low"
    }
}
