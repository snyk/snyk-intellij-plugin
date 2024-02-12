package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykCodeScanListenerLS
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNodeFromLS
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykCodeFileTreeNodeFromLS
import snyk.common.ProductType
import snyk.common.SnykError
import snyk.common.lsp.ScanIssue
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

@Suppress("DuplicatedCode")
abstract class ToolWindowSnykCodeScanListenerLS(
    val project: Project,
    private val snykToolWindowPanel: SnykToolWindowPanel,
    private val vulnerabilitiesTree: JTree,
    private val rootSecurityIssuesTreeNode: DefaultMutableTreeNode,
    private val rootQualityIssuesTreeNode: DefaultMutableTreeNode,
) : SnykCodeScanListenerLS {

    override fun scanningSnykCodeFinished(snykCodeResults: Map<SnykCodeFile, List<ScanIssue>>) {
        ApplicationManager.getApplication().invokeLater {
            displaySnykCodeResults(snykCodeResults)
            refreshAnnotationsForOpenFiles(project)
        }
    }

    override fun scanningSnykCodeError(snykError: SnykError) = Unit

    private fun displaySnykCodeResults(snykCodeResults: Map<SnykCodeFile, List<ScanIssue>>) {
        if (getSnykCachedResults(project)?.currentSnykCodeError != null) return
        if (pluginSettings().token.isNullOrEmpty()) {
            snykToolWindowPanel.displayAuthPanel()
            return
        }
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        // display Security issues
        val userObjectsForExpandedSecurityNodes =
            snykToolWindowPanel.userObjectsForExpandedNodes(rootSecurityIssuesTreeNode)
        rootSecurityIssuesTreeNode.removeAllChildren()

        var securityIssuesCount: Int? = null
        var securityIssuesHMLPostfix = ""
        if (pluginSettings().snykCodeSecurityIssuesScanEnable) {
            val securityResults = snykCodeResults
                .map { it.key to it.value.filter { issue -> issue.additionalData.isSecurityType } }
                .toMap()
            securityIssuesCount = securityResults.size
            securityIssuesHMLPostfix = buildHMLpostfix(securityResults)

            if (pluginSettings().treeFiltering.codeSecurityResults) {
                val securityResultsToDisplay = securityResults.map { entry ->
                    entry.key to entry.value
                        .filter { pluginSettings().hasSeverityEnabledAndFiltered(it.getSeverityAsEnum()) }
                }.toMap()
                displayResultsForCodeRoot(rootSecurityIssuesTreeNode, securityResultsToDisplay)
            }
        }
        snykToolWindowPanel.updateTreeRootNodesPresentation(
            securityIssuesCount = securityIssuesCount,
            addHMLPostfix = securityIssuesHMLPostfix
        )
        snykToolWindowPanel.smartReloadRootNode(
            rootSecurityIssuesTreeNode,
            userObjectsForExpandedSecurityNodes,
            selectedNodeUserObject
        )

        // display Quality (non Security) issues
        val userObjectsForExpandedQualityNodes =
            snykToolWindowPanel.userObjectsForExpandedNodes(rootQualityIssuesTreeNode)
        rootQualityIssuesTreeNode.removeAllChildren()

        var qualityIssuesCount: Int? = null
        var qualityIssuesHMLPostfix = ""
        if (pluginSettings().snykCodeQualityIssuesScanEnable) {
            val qualityResults = snykCodeResults
                .map { it.key to it.value.filter { issue -> issue.additionalData.isSecurityType } }
                .toMap()
            qualityIssuesCount = qualityResults.size
            qualityIssuesHMLPostfix = buildHMLpostfix(qualityResults)

            if (pluginSettings().treeFiltering.codeQualityResults) {
                val qualityResultsToDisplay = qualityResults.map { entry ->
                    entry.key to entry.value
                        .filter { pluginSettings().hasSeverityEnabledAndFiltered(it.getSeverityAsEnum()) }
                }.toMap()
                displayResultsForCodeRoot(rootQualityIssuesTreeNode, qualityResultsToDisplay)
            }
        }
        snykToolWindowPanel.updateTreeRootNodesPresentation(
            qualityIssuesCount = qualityIssuesCount,
            addHMLPostfix = qualityIssuesHMLPostfix
        )
        snykToolWindowPanel.smartReloadRootNode(
            rootQualityIssuesTreeNode,
            userObjectsForExpandedQualityNodes,
            selectedNodeUserObject
        )
    }

    private fun displayResultsForCodeRoot(
        rootNode: DefaultMutableTreeNode,
        issues: Map<SnykCodeFile, List<ScanIssue>>
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
                    SnykCodeFileTreeNodeFromLS(entry, productType)
                rootNode.add(fileTreeNode)
                entry.value.sortedByDescending { it.getSeverityAsEnum() }
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

    private fun buildHMLpostfix(securityResults: Map<SnykCodeFile, List<ScanIssue>>): String {
        val critical = securityResults.values.flatten().count { it.getSeverityAsEnum() == Severity.CRITICAL }
        val high = securityResults.values.flatten().count { it.getSeverityAsEnum() == Severity.HIGH }
        val medium = securityResults.values.flatten().count { it.getSeverityAsEnum() == Severity.MEDIUM }
        val low = securityResults.values.flatten().count { it.getSeverityAsEnum() == Severity.LOW }
        return " ($critical/$high/$medium/$low)"
    }
}
