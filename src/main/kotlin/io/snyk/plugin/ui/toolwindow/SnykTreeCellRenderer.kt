package io.snyk.plugin.ui.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import io.snyk.plugin.SnykFile
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.getDisabledIcon
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootContainerIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ChooseBranchNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ErrorTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.InfoTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykFileTreeNode
import snyk.common.ProductType
import snyk.common.SnykError
import snyk.common.lsp.ScanIssue
import snyk.container.ContainerIssue
import snyk.container.ContainerIssuesForImage
import snyk.container.ui.ContainerImageTreeNode
import snyk.container.ui.ContainerIssueTreeNode
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

private const val MAX_FILE_TREE_NODE_LENGTH = 60

class SnykTreeCellRenderer : ColoredTreeCellRenderer() {
    @Suppress("UNCHECKED_CAST")
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ) {
        var nodeIcon: Icon? = null
        var text: String? = null
        var attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
        when (value) {
            is SuggestionTreeNode -> {
                val issue = value.userObject as ScanIssue
                nodeIcon = SnykIcons.getSeverityIcon(issue.getSeverityAsEnum())

                val parentFileNode = value.parent as SnykFileTreeNode
                val (entry, productType) =
                    parentFileNode.userObject as Pair<Map.Entry<SnykFile, List<ScanIssue>>, ProductType>

                text =
                    "${if (issue.isIgnored()) " [ Ignored ]" else ""}${if (issue.hasAIFix()) " ⚡️" else ""} ${issue.longTitle()}"
                val cachedIssues = getSnykCachedResultsForProduct(entry.key.project, productType)
                val entryIssues = cachedIssues?.get(entry.key) ?: emptyList()
                if (!entryIssues.issueIsContained(issue)) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is SnykFileTreeNode -> {
                val (entry, productType) =
                    value.userObject as Pair<Map.Entry<SnykFile, List<ScanIssue>>, ProductType>
                val file = entry.key

                val pair = updateTextTooltipAndIcon(file, productType, value, entry.value.first())
                pair.first?.let { nodeIcon = pair.first }
                text = pair.second
                val allIssues = getSnykCachedResultsForProduct(entry.key.project, productType)
                val cachedIssues = allIssues?.get(entry.key) ?: emptyList()
                if (cachedIssues.isEmpty()) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is ContainerImageTreeNode -> {
                val issuesForImage = value.userObject as ContainerIssuesForImage
                nodeIcon = SnykIcons.CONTAINER_IMAGE
                text = issuesForImage.imageName + ProductType.CONTAINER.getCountText(value.childCount)

                val snykCachedResults = getSnykCachedResults(value.project)
                if (snykCachedResults?.currentContainerResult == null || issuesForImage.obsolete) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                    text += OBSOLETE_SUFFIX
                }
            }

            is ErrorTreeNode -> {
                val snykError = value.userObject as SnykError
                text = snykError.path + " - " + snykError.message
                nodeIcon = AllIcons.General.Error
            }

            is ChooseBranchNode -> {
                text = value.info
                nodeIcon = value.icon
            }

            is InfoTreeNode -> {
                val info = value.userObject as String
                text = info
            }

            is ContainerIssueTreeNode -> {
                val issue = value.userObject as ContainerIssue
                val snykCachedResults = getSnykCachedResults(value.project)
                nodeIcon = SnykIcons.getSeverityIcon(issue.getSeverity())
                text = issue.title +
                    when {
                        issue.ignored -> IGNORED_SUFFIX
                        snykCachedResults?.currentContainerResult == null || issue.obsolete -> OBSOLETE_SUFFIX
                        else -> ""
                    }
                if (snykCachedResults?.currentContainerResult == null || issue.ignored || issue.obsolete) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is RootOssTreeNode -> {
                val settings = pluginSettings()
                if (settings.ossScanEnable && settings.treeFiltering.ossResults) {
                    nodeIcon = SnykIcons.OPEN_SOURCE_SECURITY
                    attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                } else {
                    nodeIcon = SnykIcons.OPEN_SOURCE_SECURITY_DISABLED
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                }
                text =
                    if (settings.ossScanEnable) {
                        value.userObject.toString()
                    } else {
                        SnykToolWindowPanel.OSS_ROOT_TEXT + DISABLED_SUFFIX
                    }
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
                text =
                    if (settings.snykCodeSecurityIssuesScanEnable) {
                        value.userObject.toString()
                    } else {
                        SnykToolWindowPanel.CODE_SECURITY_ROOT_TEXT +
                            snykCodeAvailabilityPostfix()
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
                text =
                    if (settings.snykCodeQualityIssuesScanEnable) {
                        value.userObject.toString()
                    } else {
                        SnykToolWindowPanel.CODE_QUALITY_ROOT_TEXT +
                            snykCodeAvailabilityPostfix()
                                .ifEmpty { DISABLED_SUFFIX }
                    }
            }

            is RootIacIssuesTreeNode -> {
                val settings = pluginSettings()
                if (settings.iacScanEnabled && settings.treeFiltering.iacResults) {
                    nodeIcon = SnykIcons.IAC
                    attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                } else {
                    nodeIcon = SnykIcons.IAC_DISABLED
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                }
                text =
                    if (settings.iacScanEnabled) {
                        value.userObject.toString()
                    } else {
                        SnykToolWindowPanel.IAC_ROOT_TEXT + DISABLED_SUFFIX
                    }
            }

            is RootContainerIssuesTreeNode -> {
                val settings = pluginSettings()
                if (settings.containerScanEnabled && settings.treeFiltering.containerResults) {
                    nodeIcon = SnykIcons.CONTAINER
                    attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                } else {
                    nodeIcon = SnykIcons.CONTAINER_DISABLED
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                }
                text =
                    if (settings.containerScanEnabled) {
                        value.userObject.toString()
                    } else {
                        SnykToolWindowPanel.CONTAINER_ROOT_TEXT + DISABLED_SUFFIX
                    }
            }
        }
        icon = nodeIcon
        font = UIUtil.getTreeFont()
        text?.let { append(it, attributes) }
    }

    fun List<ScanIssue>.issueIsContained(issue: ScanIssue): Boolean {
        // this is a bit weird, I admit. But HashSet.contains() has an O(1) complexity.
        return this.isNotEmpty() && HashSet(this).contains(issue)
    }

    private fun updateTextTooltipAndIcon(
        file: SnykFile,
        productType: ProductType,
        value: DefaultMutableTreeNode,
        firstIssue: ScanIssue?,
    ): Pair<Icon?, String?> {

        file.relativePath.then {
            toolTipText =
                buildString {
                    append(it)
                    append(productType.getCountText(value.childCount))
                }
        }

        val text =
            toolTipText.apply {
                if (toolTipText.length > MAX_FILE_TREE_NODE_LENGTH) {
                    "..." +
                        this.substring(
                            this.length - MAX_FILE_TREE_NODE_LENGTH,
                            this.length,
                        )
                }
            }

        val nodeIcon = firstIssue?.icon()
        return Pair(nodeIcon, text)
    }

    companion object {
        private const val OBSOLETE_SUFFIX = " (obsolete)"
        private const val IGNORED_SUFFIX = " (ignored)"
        private const val DISABLED_SUFFIX = " (disabled in Settings)"
    }
}
