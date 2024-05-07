package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.SnykFile
import io.snyk.plugin.getSeverityAsEnum
import io.snyk.plugin.ui.PackageManagerIconProvider.Companion.getIcon
import io.snyk.plugin.ui.getDisabledIcon
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNodeFromLS
import io.snyk.plugin.ui.toolwindow.nodes.leaf.VulnerabilityTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootContainerIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ErrorTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.FileTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykCodeFileTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.FileTreeNodeFromLS
import snyk.common.ProductType
import snyk.common.SnykError
import snyk.common.lsp.ScanIssue
import snyk.container.ContainerIssue
import snyk.container.ContainerIssuesForImage
import snyk.container.ui.ContainerImageTreeNode
import snyk.container.ui.ContainerIssueTreeNode
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.ui.toolwindow.IacFileTreeNode
import snyk.iac.ui.toolwindow.IacIssueTreeNode
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability
import java.util.Locale
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

private const val MAX_FILE_TREE_NODE_LENGTH = 60

class SnykTreeCellRenderer : ColoredTreeCellRenderer() {
    @Suppress("UNCHECKED_CAST")
    override fun customizeCellRenderer(
        tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ) {
        var nodeIcon: Icon? = null
        var text: String? = null
        var attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES
        when (value) {
            is VulnerabilityTreeNode -> {
                val vulnerability = (value.userObject as Collection<Vulnerability>).first()
                nodeIcon = SnykIcons.getSeverityIcon(vulnerability.getSeverity())
                text = vulnerability.getPackageNameTitle()

                val snykCachedResults = getSnykCachedResults(value.project)
                if (snykCachedResults?.currentOssResults == null) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is FileTreeNode -> {
                val fileVulns = value.userObject as OssVulnerabilitiesForFile
                nodeIcon = getIcon(fileVulns.packageManager.lowercase(Locale.getDefault()))
                toolTipText =
                    buildString {
                        append(fileVulns.relativePath ?: "")
                        append(fileVulns.sanitizedTargetFile)
                        append(ProductType.OSS.getCountText(value.childCount))
                    }
                text = toolTipText.apply {
                    if (toolTipText.length > MAX_FILE_TREE_NODE_LENGTH) {
                        "..." + this.substring(
                            this.length - MAX_FILE_TREE_NODE_LENGTH, this.length
                        )
                    }
                }

                val snykCachedResults = getSnykCachedResults(value.project)
                if (snykCachedResults?.currentOssResults == null) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    text += OBSOLETE_SUFFIX
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is SuggestionTreeNode -> {
                val (suggestion, index) = value.userObject as Pair<SuggestionForFile, Int>
                nodeIcon = SnykIcons.getSeverityIcon(suggestion.getSeverityAsEnum())
                val range = suggestion.ranges[index]
                text = "line ${range.startRow}: ${
                    if (suggestion.title.isNullOrEmpty()) suggestion.message else suggestion.title
                }"
                val parentFileNode = value.parent as SnykCodeFileTreeNode
                val file = (parentFileNode.userObject as Pair<SnykFile, ProductType>).first
                if (!AnalysisData.instance.isFileInCache(file)) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is SuggestionTreeNodeFromLS -> {
                val issue = value.userObject as ScanIssue
                nodeIcon = SnykIcons.getSeverityIcon(issue.getSeverityAsEnum())

                val parentFileNode = value.parent as FileTreeNodeFromLS
                val (entry, productType) =
                    parentFileNode.userObject as Pair<Map.Entry<SnykFile, List<ScanIssue>>, ProductType>

                text = "${if (issue.isIgnored()) " [ Ignored ]" else ""}${if (issue.hasAIFix()) " ⚡️" else ""} ${issue.longTitle()}"
                val cachedIssues = getSnykCachedResultsForProduct(entry.key.project, productType)
                if (cachedIssues?.values?.flatten()?.contains(issue) == false) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is FileTreeNodeFromLS -> {
                val (entry, productType) =
                    value.userObject as Pair<Map.Entry<SnykFile, List<ScanIssue>>, ProductType>
                val file = entry.key

                val pair = updateTextTooltipAndIcon(file, productType, value, entry.value.first())
                nodeIcon = pair.first
                text = pair.second
                val cachedIssues = getSnykCachedResultsForProduct(entry.key.project, productType)
                    ?.filter { it.key.virtualFile == file.virtualFile } ?: emptyMap()
                if (cachedIssues.isEmpty()) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is SnykCodeFileTreeNode -> {
                val (file, productType) = value.userObject as Pair<SnykFile, ProductType>
                val (icon, nodeText) = updateTextTooltipAndIcon(file, productType, value, null)
                nodeIcon = icon
                text = nodeText
                if (!AnalysisData.instance.isFileInCache(file)) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    text += OBSOLETE_SUFFIX
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is IacFileTreeNode -> {
                val iacVulnerabilitiesForFile = value.userObject as IacIssuesForFile
                nodeIcon = getIcon(
                    iacVulnerabilitiesForFile.packageManager.lowercase(Locale.getDefault())
                )
                val relativePath =
                    iacVulnerabilitiesForFile.relativePath ?: iacVulnerabilitiesForFile.targetFilePath
                toolTipText = buildString {
                    append(relativePath)
                    append(ProductType.IAC.getCountText(value.childCount))
                }

                text = toolTipText.apply {
                    if (toolTipText.length > MAX_FILE_TREE_NODE_LENGTH) {
                        "..." + this.substring(
                            this.length - MAX_FILE_TREE_NODE_LENGTH, this.length
                        )
                    }
                }

                val snykCachedResults = getSnykCachedResults(value.project)
                if (snykCachedResults?.currentIacResult == null || iacVulnerabilitiesForFile.obsolete) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                    text += OBSOLETE_SUFFIX
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

            is IacIssueTreeNode -> {
                val issue = (value.userObject as IacIssue)
                val snykCachedResults = getSnykCachedResults(value.project)
                nodeIcon = SnykIcons.getSeverityIcon(issue.getSeverity())
                val prefix = if (issue.lineNumber > 0) "line ${issue.lineNumber}: " else ""
                text = prefix + issue.title + when {
                    issue.ignored -> IGNORED_SUFFIX
                    snykCachedResults?.currentIacResult == null || issue.obsolete -> OBSOLETE_SUFFIX
                    else -> ""
                }
                if (snykCachedResults?.currentIacResult == null || issue.ignored || issue.obsolete) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }

            is ContainerIssueTreeNode -> {
                val issue = value.userObject as ContainerIssue
                val snykCachedResults = getSnykCachedResults(value.project)
                nodeIcon = SnykIcons.getSeverityIcon(issue.getSeverity())
                text = issue.title + when {
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
                text = if (settings.ossScanEnable) {
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

            is RootIacIssuesTreeNode -> {
                val settings = pluginSettings()
                if (settings.iacScanEnabled && settings.treeFiltering.iacResults) {
                    nodeIcon = SnykIcons.IAC
                    attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                } else {
                    nodeIcon = SnykIcons.IAC_DISABLED
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                }
                text = if (settings.iacScanEnabled) {
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
                text = if (settings.containerScanEnabled) {
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

    private fun updateTextTooltipAndIcon(
        file: SnykFile,
        productType: ProductType,
        value: DefaultMutableTreeNode,
        firstIssue: ScanIssue?
    ): Pair<Icon?, String?> {
        val relativePath = file.relativePath
        toolTipText =
            buildString {
                append(relativePath)
                append(productType.getCountText(value.childCount))
            }

        val text = toolTipText.apply {
            if (toolTipText.length > MAX_FILE_TREE_NODE_LENGTH) {
                "..." + this.substring(
                    this.length - MAX_FILE_TREE_NODE_LENGTH, this.length
                )
            }
        }


        val nodeIcon = firstIssue?.icon() ?: file.icon
        return Pair(nodeIcon, text)
    }

    companion object {
        private const val OBSOLETE_SUFFIX = " (obsolete)"
        private const val IGNORED_SUFFIX = " (ignored)"
        private const val DISABLED_SUFFIX = " (disabled in Settings)"
    }
}
