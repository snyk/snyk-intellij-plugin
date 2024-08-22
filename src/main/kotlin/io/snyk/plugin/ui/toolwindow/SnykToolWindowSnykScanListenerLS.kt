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
import io.snyk.plugin.cancelOssIndicator
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_QUALITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_SECURITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.NODE_NOT_SUPPORTED_STATE
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.NO_OSS_FILES
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.OSS_ROOT_TEXT
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
            this.snykToolWindowPanel.updateTreeRootNodesPresentation()
            this.snykToolWindowPanel.displayScanningMessage()
        }
    }

    override fun scanningSnykCodeFinished() {
        if (disposed) return
        ApplicationManager.getApplication().invokeLater {
            this.rootSecurityIssuesTreeNode.userObject = "$CODE_SECURITY_ROOT_TEXT (scanning finished)"
            this.rootQualityIssuesTreeNode.userObject = "$CODE_QUALITY_ROOT_TEXT (scanning finished)"
            this.snykToolWindowPanel.navigateToSourceEnabled = false
            val snykCachedResults = getSnykCachedResults(project)
            displaySnykCodeResults(snykCachedResults?.currentSnykCodeResultsLS ?: emptyMap())
            refreshAnnotationsForOpenFiles(project)
            this.snykToolWindowPanel.navigateToSourceEnabled = true
        }
    }

    override fun scanningOssFinished() {
        if (disposed) return
        ApplicationManager.getApplication().invokeLater {
            cancelOssIndicator(project)
            this.rootOssIssuesTreeNode.userObject = "$OSS_ROOT_TEXT (scanning finished)"
            this.snykToolWindowPanel.navigateToSourceEnabled = false
            val snykCachedResults = getSnykCachedResults(project)
            displayOssResults(snykCachedResults?.currentOSSResultsLS ?: emptyMap())
            refreshAnnotationsForOpenFiles(project)
            this.snykToolWindowPanel.navigateToSourceEnabled = true
        }
    }

    override fun scanningError(snykScan: SnykScanParams) {
        when (snykScan.product) {
            "oss" -> {
                this.rootOssIssuesTreeNode.removeAllChildren()
                this.rootOssIssuesTreeNode.userObject = "$OSS_ROOT_TEXT (error)"
            }

            "code" -> {
                this.rootSecurityIssuesTreeNode.removeAllChildren()
                this.rootSecurityIssuesTreeNode.userObject = "$CODE_SECURITY_ROOT_TEXT (error)"
                this.rootQualityIssuesTreeNode.removeAllChildren()
                this.rootQualityIssuesTreeNode.userObject = "$CODE_QUALITY_ROOT_TEXT (error)"
            }

            "iac" -> {
                // TODO implement
            }

            "container" -> {
                // TODO implement
            }
        }
    }

    override fun onPublishDiagnostics(product: String, snykFile: SnykFile, issueList: List<ScanIssue>) {
    }

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

    fun displayOssResults(snykResults: Map<SnykFile, List<ScanIssue>>) {
        if (disposed) return
        if (getSnykCachedResults(project)?.currentOssError != null) return

        val settings = pluginSettings()

        displayIssues(
            enabledInSettings = settings.ossScanEnable,
            filterTree = settings.treeFiltering.ossResults,
            snykResults = snykResults,
            rootNode = this.rootOssIssuesTreeNode,
            ossResultsCount = snykResults.values.flatten().distinct().size,
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
                    securityIssuesCount = securityIssuesCount,
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
        securityIssuesCount: Int? = null,
        fixableIssuesCount: Int? = null,
    ) {
        if (disposed) return

        // only add these info tree nodes to Snyk Code security vulnerabilities for now
        if (securityIssuesCount == null) {
            return
        }

        val settings = pluginSettings()
        // only add these when we enable the consistent ignores flow for now
        if (!settings.isGlobalIgnoresFeatureEnabled) {
            return
        }

        var text = "✅ Congrats! No vulnerabilities found!"
        val issuesCount = issues.size
        val ignoredIssuesCount = issues.count { it.isIgnored() }
        if (issuesCount != 0) {
            if (issuesCount == 1) {
                text = "$issuesCount vulnerability found by Snyk"
            } else {
                text = "✋ $issuesCount vulnerabilities found by Snyk"
            }
            text += ", $ignoredIssuesCount ignored"
        }
        rootNode.add(
            InfoTreeNode(
                text,
                project,
            ),
        )

        if (fixableIssuesCount != null) {
            if (fixableIssuesCount > 0) {
                rootNode.add(
                    InfoTreeNode(
                        "⚡ $fixableIssuesCount vulnerabilities can be fixed by Snyk DeepCode AI",
                        project,
                    ),
                )
            } else {
                rootNode.add(
                    InfoTreeNode("There are no vulnerabilities fixable by Snyk DeepCode AI", project),
                )
            }
        }

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
                                issue,
                                navigateToSource(entry.key.virtualFile, issue.textRange ?: TextRange(0, 0)),
                            ),
                        )
                    }
            }
    }

    private fun buildSeveritiesPostfixForFileNode(results: Map<SnykFile, List<ScanIssue>>): String {
        val high = results.values.flatten().count { it.getSeverityAsEnum() == Severity.HIGH }
        val medium = results.values.flatten().count { it.getSeverityAsEnum() == Severity.MEDIUM }
        val low = results.values.flatten().count { it.getSeverityAsEnum() == Severity.LOW }
        return ": $high high, $medium medium, $low low"
    }
}
