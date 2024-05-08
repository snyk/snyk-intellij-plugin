package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.SnykFile
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_QUALITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel.Companion.CODE_SECURITY_ROOT_TEXT
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNodeFromLS
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykFileTreeNodeFromLS
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
) : SnykScanListenerLS {

    override fun scanningStarted(snykScan: SnykScanParams) {
        ApplicationManager.getApplication().invokeLater {
            rootSecurityIssuesTreeNode.userObject = "$CODE_SECURITY_ROOT_TEXT (scanning...)"
            rootQualityIssuesTreeNode.userObject = "$CODE_QUALITY_ROOT_TEXT (scanning...)"
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

    override fun scanningSnykCodeError(snykScan: SnykScanParams) = Unit

    fun displaySnykCodeResults(snykResults: Map<SnykFile, List<ScanIssue>>) {
        if (getSnykCachedResults(project)?.currentSnykCodeError != null) return
        if (pluginSettings().token.isNullOrEmpty()) {
            snykToolWindowPanel.displayAuthPanel()
            return
        }
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        // display Security issues
        displayIssues(snykResults, selectedNodeUserObject, true)

        // display Quality (non Security) issues
        displayIssues(snykResults, selectedNodeUserObject, false)
    }

    private fun displayIssues(
        snykCodeResults: Map<SnykFile, List<ScanIssue>>,
        selectedNodeUserObject: Any?,
        isSecurity: Boolean
    ) {
        val rootNode = if (isSecurity) this.rootSecurityIssuesTreeNode else this.rootQualityIssuesTreeNode

        val userObjectsForExpandedNodes =
            snykToolWindowPanel.userObjectsForExpandedNodes(rootNode)

        rootNode.removeAllChildren()

        var issuesCount: Int? = null
        var rootNodePostFix = ""
        val settings = pluginSettings()
        val enabledInSettings = when {
            isSecurity -> settings.snykCodeSecurityIssuesScanEnable
            else -> settings.snykCodeQualityIssuesScanEnable
        }

        if (enabledInSettings) {
            val filteredResults = snykCodeResults
                .map { it.key to it.value.filter { issue -> issue.additionalData.isSecurityType == isSecurity } }
                .toMap()

            issuesCount = filteredResults.values.flatten().distinct().size
            rootNodePostFix = buildSeveritiesPostfixForFileNode(filteredResults)

            val treeFiltering = when {
                isSecurity -> settings.treeFiltering.codeSecurityResults
                else -> settings.treeFiltering.codeQualityResults
            }

            if (treeFiltering) {
                val resultsToDisplay = filteredResults.map { entry ->
                    entry.key to entry.value.filter {
                        settings.hasSeverityEnabledAndFiltered(it.getSeverityAsEnum())
                    }
                }.toMap()
                displayResultsForCodeRoot(rootNode, resultsToDisplay)
            }
        }

        if (isSecurity) {
            snykToolWindowPanel.updateTreeRootNodesPresentation(
                securityIssuesCount = issuesCount,
                addHMLPostfix = rootNodePostFix
            )
        } else {
            snykToolWindowPanel.updateTreeRootNodesPresentation(
                qualityIssuesCount = issuesCount,
                addHMLPostfix = rootNodePostFix
            )
        }

        snykToolWindowPanel.smartReloadRootNode(
            rootNode,
            userObjectsForExpandedNodes,
            selectedNodeUserObject
        )
    }

    private fun displayResultsForCodeRoot(
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
                    else -> throw IllegalArgumentException(rootNode.javaClass.simpleName)
                }
                val fileTreeNode =
                    SnykFileTreeNodeFromLS(entry, productType)
                rootNode.add(fileTreeNode)
                entry.value.sortedByDescending { it.additionalData.priorityScore }
                    .forEach { issue ->
                        fileTreeNode.add(
                            SuggestionTreeNodeFromLS(
                                issue,
                                navigateToSource(entry.key.virtualFile, issue.textRange ?: TextRange(0, 0))
                            )
                        )
                    }
            }
    }

    private fun buildSeveritiesPostfixForFileNode(securityResults: Map<SnykFile, List<ScanIssue>>): String {
        val high = securityResults.values.flatten().count { it.getSeverityAsEnum() == Severity.HIGH }
        val medium = securityResults.values.flatten().count { it.getSeverityAsEnum() == Severity.MEDIUM }
        val low = securityResults.values.flatten().count { it.getSeverityAsEnum() == Severity.LOW }
        return ": $high high, $medium medium, $low low"
    }
}
