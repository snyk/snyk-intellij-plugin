@file:Suppress("DuplicatedCode")

package io.snyk.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.ui.getDisabledIcon
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNodeFromLS
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ErrorTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykCodeFileTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykCodeFileTreeNodeFromLS
import snyk.common.ProductType
import snyk.common.SnykError
import snyk.common.lsp.ScanIssue
import javax.swing.Icon
import javax.swing.JTree

private const val MAX_FILE_TREE_NODE_LENGTH = 60

class SnykTreeCellRendererFromLS : ColoredTreeCellRenderer() {
    @Suppress("UNCHECKED_CAST")
    override fun customizeCellRenderer(
        tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ) {
        var nodeIcon: Icon? = null
        var text: String? = null
        var attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
        when (value) {
            is SuggestionTreeNodeFromLS -> {
                val issue = value.userObject as ScanIssue
                nodeIcon = SnykIcons.getSeverityIcon(issue.getSeverityAsEnum())
                val range = issue.range
                text = "line ${range.start.line}: ${
                    issue.title.ifEmpty { issue.additionalData.message }
                }"
                val parentFileNode = value.parent as SnykCodeFileTreeNode
                val entry =
                    (parentFileNode.userObject as Pair<Map.Entry<SnykCodeFile, List<ScanIssue>>, ProductType>).first
                val cachedIssues = getSnykCachedResults(entry.key.project)?.currentSnykCodeResultsLS
                if (cachedIssues?.values?.flatten()?.contains(issue) == false) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is SnykCodeFileTreeNodeFromLS -> {
                val (entry, productType) = value.userObject as Pair<Map.Entry<SnykCodeFile, List<ScanIssue>>, ProductType>
                val file = entry.key
                val relativePath = file.relativePath
                toolTipText =
                    buildString {
                        append(relativePath)
                        append(productType.getCountText(value.childCount))
                    }

                text = toolTipText.apply {
                    if (toolTipText.length > MAX_FILE_TREE_NODE_LENGTH) {
                        "..." + this.substring(
                            this.length - MAX_FILE_TREE_NODE_LENGTH, this.length
                        )
                    }
                }

                nodeIcon = file.icon
                val cachedIssues = getSnykCachedResults(file.project)?.currentSnykCodeResultsLS
                    ?.filter { it.key.virtualFile == file.virtualFile } ?: emptyMap()
                if (cachedIssues.isEmpty()) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is ErrorTreeNode -> {
                val snykError = value.userObject as SnykError
                text = snykError.path + " - " + snykError.message
                nodeIcon = AllIcons.General.Error
            }

            is RootSecurityIssuesTreeNode -> {
                val settings = pluginSettings()
                if (settings.snykCodeSecurityIssuesScanEnable && settings.treeFiltering.codeSecurityResults) {
                    nodeIcon = SnykIcons.SNYK_CODE
                    attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                } else {
                    nodeIcon = SnykIcons.SNYK_CODE_DISABLED
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                }
                text = if (settings.snykCodeSecurityIssuesScanEnable) {
                    value.userObject.toString()
                } else {
                    SnykToolWindowPanel.CODE_SECURITY_ROOT_TEXT + snykCodeAvailabilityPostfix()
                        .ifEmpty { DISABLED_SUFFIX }
                }
            }

            is RootQualityIssuesTreeNode -> {
                val settings = pluginSettings()
                if (settings.snykCodeQualityIssuesScanEnable && settings.treeFiltering.codeQualityResults) {
                    nodeIcon = SnykIcons.SNYK_CODE
                    attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                } else {
                    nodeIcon = SnykIcons.SNYK_CODE_DISABLED
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                }
                text = if (settings.snykCodeQualityIssuesScanEnable) {
                    value.userObject.toString()
                } else {
                    SnykToolWindowPanel.CODE_QUALITY_ROOT_TEXT + snykCodeAvailabilityPostfix()
                        .ifEmpty { DISABLED_SUFFIX }
                }
            }
        }
        icon = nodeIcon
        font = UIUtil.getTreeFont()
        text?.let { append(it, attributes) }
    }

    companion object {
        private const val OBSOLETE_SUFFIX = " (obsolete)"
        private const val IGNORED_SUFFIX = " (ignored)"
        private const val DISABLED_SUFFIX = " (disabled in Settings)"
    }
}
