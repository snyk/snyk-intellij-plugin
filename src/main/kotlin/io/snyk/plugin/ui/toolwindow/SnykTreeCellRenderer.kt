package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Iconable
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import icons.SnykIcons
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.PDU
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.snykcode.getSeverityAsEnum
import io.snyk.plugin.ui.PackageManagerIconProvider
import io.snyk.plugin.ui.getDisabledIcon
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.leaf.VulnerabilityTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootContainerIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ErrorTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.FileTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykCodeFileTreeNode
import snyk.common.ProductType
import snyk.common.SnykError
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

class SnykTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
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
                nodeIcon = PackageManagerIconProvider.getIcon(fileVulns.packageManager.lowercase(Locale.getDefault()))
                text = fileVulns.sanitizedTargetFile + ProductType.OSS.getCountText(value.childCount)

                val snykCachedResults = getSnykCachedResults(value.project)
                if (snykCachedResults?.currentOssResults == null) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    text += obsoleteSuffix
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
                val file = (parentFileNode.userObject as Pair<SnykCodeFile, ProductType>).first
                if (!AnalysisData.instance.isFileInCache(file)) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }
            is SnykCodeFileTreeNode -> {
                val (file, productType) = value.userObject as Pair<SnykCodeFile, ProductType>
                text = PDU.toSnykCodeFile(file).virtualFile.name + productType.getCountText(value.childCount)
                val psiFile = PDU.toPsiFile(file)
                nodeIcon = psiFile?.getIcon(Iconable.ICON_FLAG_READ_STATUS)
                if (!AnalysisData.instance.isFileInCache(file)) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    text += obsoleteSuffix
                    nodeIcon = getDisabledIcon(nodeIcon)
                }
            }
            is IacFileTreeNode -> {
                val iacVulnerabilitiesForFile = value.userObject as IacIssuesForFile
                nodeIcon = PackageManagerIconProvider.getIcon(
                    iacVulnerabilitiesForFile.packageManager.lowercase(Locale.getDefault())
                )
                text = iacVulnerabilitiesForFile.targetFile + ProductType.IAC.getCountText(value.childCount)

                val snykCachedResults = getSnykCachedResults(value.project)
                if (snykCachedResults?.currentIacResult == null || iacVulnerabilitiesForFile.obsolete) {
                    attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
                    nodeIcon = getDisabledIcon(nodeIcon)
                    text += obsoleteSuffix
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
                    text += obsoleteSuffix
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
                    issue.ignored -> ignoredSuffix
                    snykCachedResults?.currentIacResult == null || issue.obsolete -> obsoleteSuffix
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
                    issue.ignored -> ignoredSuffix
                    snykCachedResults?.currentContainerResult == null || issue.obsolete -> obsoleteSuffix
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
                    SnykToolWindowPanel.OSS_ROOT_TEXT + disabledSuffix
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
                    SnykToolWindowPanel.CODE_SECURITY_ROOT_TEXT +
                        snykCodeAvailabilityPostfix().ifEmpty { disabledSuffix }
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
                    SnykToolWindowPanel.CODE_QUALITY_ROOT_TEXT +
                        snykCodeAvailabilityPostfix().ifEmpty { disabledSuffix }
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
                    SnykToolWindowPanel.IAC_ROOT_TEXT + disabledSuffix
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
                    SnykToolWindowPanel.CONTAINER_ROOT_TEXT + disabledSuffix
                }
            }
        }
        icon = nodeIcon
        font = UIUtil.getTreeFont()
        text?.let { append(it, attributes) }
    }

    companion object {
        private const val obsoleteSuffix = " (obsolete)"
        private const val ignoredSuffix = " (ignored)"
        private const val disabledSuffix = " (disabled in Settings)"
    }
}
