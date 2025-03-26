package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
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
import snyk.common.lsp.FilterableIssueType
import snyk.common.lsp.LsProduct
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

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
                removeChildrenAndRefresh(rootOssIssuesTreeNode)
                this.rootOssIssuesTreeNode.userObject = "$OSS_ROOT_TEXT (error)"
            }

            LsProduct.Code -> {
                removeChildrenAndRefresh(rootSecurityIssuesTreeNode)
                rootSecurityIssuesTreeNode.userObject = "$CODE_SECURITY_ROOT_TEXT (error)"
                removeChildrenAndRefresh(rootQualityIssuesTreeNode)
                rootQualityIssuesTreeNode.userObject = "$CODE_QUALITY_ROOT_TEXT (error)"
            }

            LsProduct.InfrastructureAsCode -> {
                removeChildrenAndRefresh(rootIacIssuesTreeNode)
                rootIacIssuesTreeNode.userObject = "$IAC_ROOT_TEXT (error)"
            }

            LsProduct.Container -> Unit
            LsProduct.Unknown -> Unit
        }
        refreshAnnotationsForOpenFiles(project)
    }

    private fun removeChildrenAndRefresh(node: DefaultMutableTreeNode) {
        node.removeAllChildren()
        invokeLater{ (vulnerabilitiesTree.model as DefaultTreeModel).nodeStructureChanged(node) }
    }

    override fun onPublishDiagnostics(product: LsProduct, snykFile: SnykFile, issueList: List<ScanIssue>) {}

    fun displaySnykCodeResults(snykResults: Map<SnykFile, List<ScanIssue>>) {
        if (disposed) return

        val settings = pluginSettings()

        // display Security issues
        val securityIssues =
            snykResults
                .map { it.key to it.value.filter { issue -> issue.additionalData.isSecurityType } }
                .toMap()

        displayIssues(
            filterableIssueType = ScanIssue.CODE_SECURITY,
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
            filterableIssueType = ScanIssue.CODE_QUALITY,
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

        val flattenedResults = snykResults.values.flatten()

        when (issueType) {
            ScanIssue.OPEN_SOURCE -> {
                val ossResultsCount =
                    flattenedResults.filter { it.filterableIssueType == ScanIssue.OPEN_SOURCE }.distinct().size
                displayIssues(
                    filterableIssueType = ScanIssue.OPEN_SOURCE,
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
                    filterableIssueType = ScanIssue.INFRASTRUCTURE_AS_CODE,
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
                    filterableIssueType = filterableIssueType,
                    rootNode = rootNode,
                    issues = snykResults.values.flatten().distinct(),
                    fixableIssuesCount = fixableIssuesCount,
                )

                val resultsToDisplay =
                    snykResults.map { entry ->
                        entry.key to
                            entry.value.filter {
                                settings.hasSeverityEnabledAndFiltered(it.getSeverityAsEnum())
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

    private fun getIssueFoundText(issuesCount: Int): String {
        if (pluginSettings().isGlobalIgnoresFeatureEnabled && !pluginSettings().openIssuesEnabled) {
            return "Open issues are disabled!"
        }

        return if (issuesCount == 0) "✅ Congrats! No issues found!" else
            "✋ $issuesCount issue${if (issuesCount == 1) "" else "s" }"
    }

    private fun getIssueFoundTextForCodeSecurity(totalIssuesCount: Int, openIssuesCount: Int, ignoredIssuesCount: Int): String {
        if (!pluginSettings().isGlobalIgnoresFeatureEnabled) {
            return getIssueFoundText(totalIssuesCount)
        }

        val openIssuesText = "$openIssuesCount open issue${if (openIssuesCount == 1) "" else "s" }"
        val ignoredIssuesText = "$ignoredIssuesCount ignored issue${if (ignoredIssuesCount == 1) "" else "s" }"

        return if (pluginSettings().openIssuesEnabled) {
            if (pluginSettings().ignoredIssuesEnabled) {
                if (totalIssuesCount == 0) {
                    "✅ Congrats! No issues found!"
                } else {
                    "✋ $openIssuesText, $ignoredIssuesText"
                }
            } else {
                if (openIssuesCount == 0) {
                    "✅ Congrats! No open issues found!"
                } else {
                    "✋ $openIssuesText"
                }
            }
        } else if (pluginSettings().ignoredIssuesEnabled) {
            if (ignoredIssuesCount == 0) {
                "✋ No ignored issues, open issues are disabled"
            } else {
                "✋ $ignoredIssuesText, open issues are disabled"
            }
        } else {
            "Open and Ignored issues are disabled!" // In theory, this is prevented by IntelliJ
        }
    }

    private fun getNoIssueViewOptionsSelectedTreeNode(): InfoTreeNode? {
        if (!pluginSettings().isGlobalIgnoresFeatureEnabled) {
            return null
        }

        if (!pluginSettings().openIssuesEnabled) {
            return InfoTreeNode(
                "Adjust your settings to view Open issues.",
                project,
            )
        }

        return null;
    }

    private fun getNoIssueViewOptionsSelectedTreeNodeForCodeSecurity(): InfoTreeNode? {
        if (!pluginSettings().isGlobalIgnoresFeatureEnabled) {
            return null
        }

        if (!pluginSettings().openIssuesEnabled) {
            return InfoTreeNode(
                "Adjust your settings to view Open issues.",
                project,
            )
        }

        if (!pluginSettings().ignoredIssuesEnabled) {
                return InfoTreeNode(
                    "Adjust your settings to view Ignored issues.",
                    project,
                )
        }

        return null;
    }

    private fun getFixableIssuesNode(fixableIssuesCount: Int): InfoTreeNode {
        return InfoTreeNode(
            if (fixableIssuesCount > 0) "⚡ $fixableIssuesCount issue${if (fixableIssuesCount == 1) "" else "s"} can be fixed automatically" else "There are no issues automatically fixable",
            project,
        )
    }

    private fun getFixableIssuesNodeForCodeSecurity(fixableIssuesCount: Int): InfoTreeNode? {
        if (pluginSettings().isGlobalIgnoresFeatureEnabled && !pluginSettings().openIssuesEnabled) {
            return null
        }
        return getFixableIssuesNode(fixableIssuesCount)
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

        val totalIssuesCount = issues.size
        val ignoredIssuesCount = issues.count { it.isIgnored() }
        val openIssuesCount = totalIssuesCount - ignoredIssuesCount
        val text = if (filterableIssueType == ScanIssue.CODE_SECURITY) getIssueFoundTextForCodeSecurity(totalIssuesCount, openIssuesCount, ignoredIssuesCount) else getIssueFoundText(totalIssuesCount)
        rootNode.add(
            InfoTreeNode(
                text,
                project,
            ),
        )

        if (totalIssuesCount == 0) {
            val ivoNode = if (filterableIssueType == ScanIssue.CODE_SECURITY) getNoIssueViewOptionsSelectedTreeNodeForCodeSecurity() else getNoIssueViewOptionsSelectedTreeNode()
            if (ivoNode != null) {
                rootNode.add(ivoNode)
            }
        } else if (fixableIssuesCount != null) {
            val fixableNode = if (filterableIssueType == ScanIssue.CODE_SECURITY) getFixableIssuesNodeForCodeSecurity(fixableIssuesCount) else getFixableIssuesNode(fixableIssuesCount)
            if (fixableNode != null) {
                rootNode.add(fixableNode)
            }
        }
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
