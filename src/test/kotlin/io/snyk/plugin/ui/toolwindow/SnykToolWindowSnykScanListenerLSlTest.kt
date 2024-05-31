package io.snyk.plugin.ui.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.treeStructure.Tree
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.Severity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import junit.framework.TestCase
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import snyk.common.ProductType
import snyk.common.lsp.CommitChangeLine
import snyk.common.lsp.DataFlow
import snyk.common.lsp.ExampleCommitFix
import snyk.common.lsp.ScanIssue
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class SnykToolWindowSnykScanListenerLSlTest : BasePlatformTestCase() {
    private lateinit var cut: SnykToolWindowSnykScanListenerLS
    private lateinit var snykToolWindowPanel: SnykToolWindowPanel
    private lateinit var vulnerabilitiesTree: JTree
    private lateinit var rootTreeNode: DefaultMutableTreeNode
    private lateinit var rootOssIssuesTreeNode: DefaultMutableTreeNode
    private lateinit var rootSecurityIssuesTreeNode: DefaultMutableTreeNode
    private lateinit var rootQualityIssuesTreeNode: DefaultMutableTreeNode

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        snykToolWindowPanel = SnykToolWindowPanel(project)
        rootOssIssuesTreeNode = RootOssTreeNode(project)
        rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
        rootQualityIssuesTreeNode = RootQualityIssuesTreeNode(project)
    }

    private fun mockScanIssues(): List<ScanIssue> {
        val issue = mockk<ScanIssue>(relaxed = true)
        every { issue.getSeverityAsEnum() } returns Severity.CRITICAL
        every { issue.title() } returns "title"
        every { issue.issueNaming() } returns "issueNaming"
        every { issue.isIgnored() } returns false
        every { issue.cwes() } returns emptyList()
        every { issue.cves() } returns emptyList()
        every { issue.cvssV3() } returns null
        every { issue.cvssScore() } returns null
        every { issue.id() } returns "id"
        every { issue.additionalData.getProductType() } returns ProductType.CODE_SECURITY
        every { issue.additionalData.message } returns "Test message"
        every { issue.additionalData.repoDatasetSize } returns 1
        every { issue.additionalData.exampleCommitFixes } returns
            listOf(
                ExampleCommitFix(
                    "https://commit-url",
                    listOf(
                        CommitChangeLine("1", 1, "lineChange"),
                    ),
                ),
            )
        every {
            issue.additionalData.dataFlow
        } returns listOf(DataFlow(0, getTestDataPath(), Range(Position(1, 1), Position(1, 1)), ""))
        return listOf(issue)
    }

    fun `testAddInfoTreeNodes does not add new tree nodes for non-code security`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        // setup the rootTreeNode from scratch
        rootTreeNode = DefaultMutableTreeNode("")
        rootTreeNode.add(rootOssIssuesTreeNode)
        rootTreeNode.add(rootSecurityIssuesTreeNode)
        rootTreeNode.add(rootQualityIssuesTreeNode)
        vulnerabilitiesTree =
            Tree(rootTreeNode).apply {
                this.isRootVisible = false
            }

        cut =
            SnykToolWindowSnykScanListenerLS(
                project,
                snykToolWindowPanel,
                vulnerabilitiesTree,
                rootSecurityIssuesTreeNode,
                rootQualityIssuesTreeNode,
                rootOssIssuesTreeNode,
            )

        TestCase.assertEquals(3, rootTreeNode.childCount)
        cut.addInfoTreeNodes(rootTreeNode, mockScanIssues())
        TestCase.assertEquals(3, rootTreeNode.childCount)
    }

    fun `testAddInfoTreeNodes does not add new tree nodes for code security if ignores are not enabled`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = false

        // setup the rootTreeNode from scratch
        rootTreeNode = DefaultMutableTreeNode("")
        rootTreeNode.add(rootOssIssuesTreeNode)
        rootTreeNode.add(rootSecurityIssuesTreeNode)
        rootTreeNode.add(rootQualityIssuesTreeNode)
        vulnerabilitiesTree =
            Tree(rootTreeNode).apply {
                this.isRootVisible = false
            }

        cut =
            SnykToolWindowSnykScanListenerLS(
                project,
                snykToolWindowPanel,
                vulnerabilitiesTree,
                rootSecurityIssuesTreeNode,
                rootQualityIssuesTreeNode,
                rootOssIssuesTreeNode,
            )

        TestCase.assertEquals(3, rootTreeNode.childCount)
        cut.addInfoTreeNodes(rootTreeNode, mockScanIssues(), 1, 1)
        TestCase.assertEquals(3, rootTreeNode.childCount)
    }

    fun `testAddInfoTreeNodes adds new tree nodes for code security if ignores are enabled`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true

        // setup the rootTreeNode from scratch
        rootTreeNode = DefaultMutableTreeNode("")
        rootTreeNode.add(rootOssIssuesTreeNode)
        rootTreeNode.add(rootSecurityIssuesTreeNode)
        rootTreeNode.add(rootQualityIssuesTreeNode)
        vulnerabilitiesTree =
            Tree(rootTreeNode).apply {
                this.isRootVisible = false
            }

        cut =
            SnykToolWindowSnykScanListenerLS(
                project,
                snykToolWindowPanel,
                vulnerabilitiesTree,
                rootSecurityIssuesTreeNode,
                rootQualityIssuesTreeNode,
                rootOssIssuesTreeNode,
            )

        TestCase.assertEquals(3, rootTreeNode.childCount)
        cut.addInfoTreeNodes(rootTreeNode, mockScanIssues(), 1, 1)
        TestCase.assertEquals(5, rootTreeNode.childCount)
    }
}
