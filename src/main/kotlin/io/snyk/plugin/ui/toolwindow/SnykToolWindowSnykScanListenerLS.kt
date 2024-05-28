package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_QUALITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_SECURITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.OSS_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootContainerIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykCodeFileTreeNode
import snyk.common.ProductType
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
) : SnykScanListenerLS {

    override fun scanningStarted(snykScan: SnykScanParams) {
        ApplicationManager.getApplication().invokeLater {
            rootSecurityIssuesTreeNode.userObject = "$CODE_SECURITY_ROOT_TEXT (scanning...)"
            rootQualityIssuesTreeNode.userObject = "$CODE_QUALITY_ROOT_TEXT (scanning...)"
            rootOssIssuesTreeNode.userObject = "$OSS_ROOT_TEXT (scanning...)"
        }
    }

    override fun scanningSnykCodeFinished(snykResults: Map<SnykFile, List<ScanIssue>>) {
        ApplicationManager.getApplication().invokeLater {
            this.snykToolWindowPanel.navigateToSourceEnabled = false
            displaySnykCodeResults(snykResults)
            refreshAnnotationsForOpenFiles(project)
            this.snykToolWindowPanel.navigateToSourceEnabled = true
        }
    }

    override fun scanningOssFinished(snykResults: Map<SnykFile, List<ScanIssue>>) {
        ApplicationManager.getApplication().invokeLater {
            this.snykToolWindowPanel.navigateToSourceEnabled = false
            displayOssResults(snykResults)
            refreshAnnotationsForOpenFiles(project)
            this.snykToolWindowPanel.navigateToSourceEnabled = true
        }
    }

    override fun scanningError(snykScan: SnykScanParams) = Unit

    fun displaySnykCodeResults(snykResults: Map<SnykFile, List<ScanIssue>>) {
        if (getSnykCachedResults(project)?.currentSnykCodeError != null) return

        val settings = pluginSettings()


        // display Security issues
        val securityIssues = snykResults
            .map { it.key to it.value.filter { issue -> issue.additionalData.isSecurityType } }
            .toMap()
        displayIssues(
            enabledInSettings = settings.snykCodeSecurityIssuesScanEnable,
            filterTree =  settings.treeFiltering.codeSecurityResults,
            snykResults = securityIssues,
            rootNode = this.rootSecurityIssuesTreeNode,
            securityIssuesCount = securityIssues.values.flatten().distinct().size,
        )

        // display Quality (non Security) issues
        val qualityIssues = snykResults
            .map { it.key to it.value.filter { issue -> !issue.additionalData.isSecurityType } }
            .toMap()

        displayIssues(
            enabledInSettings = settings.snykCodeQualityIssuesScanEnable,
            filterTree =  settings.treeFiltering.codeQualityResults,
            snykResults = qualityIssues,
            rootNode = this.rootQualityIssuesTreeNode,
            qualityIssuesCount = qualityIssues.values.flatten().distinct().size,
        )
    }


    fun displayOssResults(snykResults: Map<SnykFile, List<ScanIssue>>) {
        if (getSnykCachedResults(project)?.currentOssError != null) return

        val settings = pluginSettings()

        displayIssues(
            enabledInSettings = settings.ossScanEnable,
            filterTree =  settings.treeFiltering.ossResults,
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
    ) {
        if (pluginSettings().token.isNullOrEmpty()) {
            snykToolWindowPanel.displayAuthPanel()
            return
        }

        val userObjectsForExpandedNodes =
            snykToolWindowPanel.userObjectsForExpandedNodes(rootNode)
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)
        val settings = pluginSettings()

        rootNode.removeAllChildren()

        var rootNodePostFix = ""

        if (enabledInSettings) {
            rootNodePostFix = buildSeveritiesPostfixForFileNode(snykResults)

            if (filterTree) {
                val resultsToDisplay = snykResults.map { entry ->
                    entry.key to entry.value.filter {
                        settings.hasSeverityEnabledAndFiltered(it.getSeverityAsEnum())
                    }
                }.toMap()
                displayResultsForRootTreeNode(rootNode, resultsToDisplay)
            }
        }

        snykToolWindowPanel.updateTreeRootNodesPresentation(
            securityIssuesCount = securityIssuesCount,
            qualityIssuesCount = qualityIssuesCount,
            ossResultsCount = ossResultsCount,
            iacResultsCount = iacResultsCount,
            containerResultsCount = containerResultsCount,
            addHMLPostfix = rootNodePostFix
        )

        snykToolWindowPanel.smartReloadRootNode(
            rootNode,
            userObjectsForExpandedNodes,
            selectedNodeUserObject
        )
    }

    private fun displayResultsForRootTreeNode(
        rootNode: DefaultMutableTreeNode,
        issues: Map<SnykFile, List<ScanIssue>>
    ) {
        fun navigateToSource(virtualFile: VirtualFile, textRange: TextRange): () -> Unit = {
            io.snyk.plugin.navigateToSource(project, virtualFile, textRange.startOffset, textRange.endOffset)
        }
        issues
            .filter { it.value.isNotEmpty() }
            .forEach { entry ->
                val productType = when (rootNode) {
                    is RootQualityIssuesTreeNode -> ProductType.CODE_QUALITY
                    is RootSecurityIssuesTreeNode -> ProductType.CODE_SECURITY
                    is RootOssTreeNode -> ProductType.OSS
                    is RootContainerIssuesTreeNode -> ProductType.CONTAINER
                    is RootIacIssuesTreeNode -> ProductType.IAC
                    else -> throw IllegalArgumentException(rootNode.javaClass.simpleName)
                }

                val fileTreeNode =
                    SnykCodeFileTreeNode(entry, productType)
                rootNode.add(fileTreeNode)
                entry.value.sortedByDescending { it.priority()}
                    .forEach { issue ->
                        fileTreeNode.add(
                            SuggestionTreeNode(
                                issue,
                                navigateToSource(entry.key.virtualFile, issue.textRange ?: TextRange(0, 0))
                            )
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
