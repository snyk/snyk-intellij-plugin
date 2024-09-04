package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.treeStructure.Tree
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
import snyk.common.annotator.SnykCodeAnnotator
import snyk.common.lsp.DataFlow
import snyk.common.lsp.IssueData
import snyk.common.lsp.ScanIssue
import java.nio.file.Paths
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class SnykToolWindowSnykScanListenerLSTest : BasePlatformTestCase() {
    private lateinit var cut: SnykToolWindowSnykScanListenerLS
    private lateinit var snykToolWindowPanel: SnykToolWindowPanel
    private lateinit var vulnerabilitiesTree: JTree
    private lateinit var rootTreeNode: DefaultMutableTreeNode
    private lateinit var rootOssIssuesTreeNode: DefaultMutableTreeNode
    private lateinit var rootSecurityIssuesTreeNode: DefaultMutableTreeNode
    private lateinit var rootQualityIssuesTreeNode: DefaultMutableTreeNode

    private val fileName = "app.js"
    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    override fun getTestDataPath(): String {
        val resource = SnykCodeAnnotator::class.java.getResource("/test-fixtures/code/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }

        snykToolWindowPanel = SnykToolWindowPanel(project)
        rootOssIssuesTreeNode = RootOssTreeNode(project)
        rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
        rootQualityIssuesTreeNode = RootQualityIssuesTreeNode(project)
    }

    private fun mockScanIssues(
        isIgnored: Boolean? = false,
        hasAIFix: Boolean? = false,
    ): List<ScanIssue> {
        val issue =
            ScanIssue(
                id = "id",
                title = "title",
                severity = Severity.CRITICAL.toString(),
                filePath = getTestDataPath(),
                range = Range(),
                additionalData =
                    IssueData(
                        message = "Test message",
                        leadURL = "",
                        rule = "",
                        repoDatasetSize = 1,
                        exampleCommitFixes = listOf(),
                        cwe = emptyList(),
                        text = "",
                        markers = null,
                        cols = null,
                        rows = null,
                        isSecurityType = true,
                        priorityScore = 0,
                        hasAIFix = hasAIFix!!,
                        dataFlow = listOf(DataFlow(0, getTestDataPath(), Range(Position(1, 1), Position(1, 1)), "")),
                        license = null,
                        identifiers = null,
                        description = "",
                        language = "",
                        packageManager = "",
                        packageName = "",
                        name = "",
                        version = "",
                        exploit = null,
                        CVSSv3 = null,
                        cvssScore = null,
                        fixedIn = null,
                        from = listOf(),
                        upgradePath = listOf(),
                        isPatchable = false,
                        isUpgradable = false,
                        projectName = "",
                        displayTargetFile = null,
                        matchingIssues = listOf(),
                        lesson = null,
                        details = "",
                        ruleId = "",
                    ),
                isIgnored = isIgnored,
                ignoreDetails = null,
            )
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
        cut.addInfoTreeNodes(rootTreeNode, mockScanIssues(), 1)
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
        cut.addInfoTreeNodes(rootTreeNode, mockScanIssues(), 1)
        TestCase.assertEquals(5, rootTreeNode.childCount)
        TestCase.assertEquals(rootTreeNode.children().toList()[0].toString(), " Open Source")
        TestCase.assertEquals(rootTreeNode.children().toList()[1].toString(), " Code Security")
        TestCase.assertEquals(rootTreeNode.children().toList()[2].toString(), " Code Quality")
        TestCase.assertEquals(
            rootTreeNode.children().toList()[3].toString(),
            "1 vulnerability found by Snyk, 0 ignored",
        )
        TestCase.assertEquals(
            rootTreeNode.children().toList()[4].toString(),
            "⚡ 1 vulnerabilities can be fixed by Snyk DeepCode AI",
        )
    }

    fun `testAddInfoTreeNodes adds new tree nodes for code security if all ignored issues are hidden`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true
        pluginSettings().ignoredIssuesEnabled = false

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
        cut.addInfoTreeNodes(rootTreeNode, mockScanIssues(true), 1)
        TestCase.assertEquals(6, rootTreeNode.childCount)
    }

    fun `testAddInfoTreeNodes adds new tree nodes for code security if all open issues are hidden`() {
        pluginSettings().isGlobalIgnoresFeatureEnabled = true
        pluginSettings().openIssuesEnabled = false

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
        cut.addInfoTreeNodes(rootTreeNode, mockScanIssues(false), 1)
        TestCase.assertEquals(6, rootTreeNode.childCount)
    }
}
