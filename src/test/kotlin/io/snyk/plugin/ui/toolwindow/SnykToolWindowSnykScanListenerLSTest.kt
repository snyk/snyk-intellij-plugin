package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.treeStructure.Tree
import io.mockk.unmockkAll
import io.snyk.plugin.Severity
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import junit.framework.TestCase
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import snyk.common.annotator.SnykCodeAnnotator
import snyk.common.lsp.DataFlow
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.IssueData
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.trust.WorkspaceTrustSettings
import java.nio.file.Paths
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.absolutePathString

class SnykToolWindowSnykScanListenerLSTest : BasePlatformTestCase() {
    private lateinit var cut: SnykToolWindowSnykScanListenerLS
    private lateinit var snykToolWindowPanel: SnykToolWindowPanel
    private lateinit var vulnerabilitiesTree: JTree
    private lateinit var rootTreeNode: DefaultMutableTreeNode
    private lateinit var rootOssIssuesTreeNode: DefaultMutableTreeNode
    private lateinit var rootSecurityIssuesTreeNode: DefaultMutableTreeNode
    private lateinit var rootQualityIssuesTreeNode: DefaultMutableTreeNode
    private lateinit var rootIacIssuesTreeNode: DefaultMutableTreeNode

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
        val contentRootPaths = project.getContentRootPaths()
        service<FolderConfigSettings>()
            .addFolderConfig(
                FolderConfig(
                    contentRootPaths.first().toAbsolutePath().toString(), "main"
                )
            )
        snykToolWindowPanel = SnykToolWindowPanel(project)
        rootOssIssuesTreeNode = RootOssTreeNode(project)
        rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
        rootQualityIssuesTreeNode = RootQualityIssuesTreeNode(project)
        rootIacIssuesTreeNode = RootIacIssuesTreeNode(project)
        pluginSettings().setDeltaEnabled(enabled = true)
        contentRootPaths.forEach { service<WorkspaceTrustSettings>().addTrustedPath(it.root.absolutePathString())}

        rootTreeNode = DefaultMutableTreeNode("")
        rootTreeNode.add(rootOssIssuesTreeNode)
        rootTreeNode.add(rootSecurityIssuesTreeNode)
        rootTreeNode.add(rootQualityIssuesTreeNode)
        rootTreeNode.add(rootIacIssuesTreeNode)
        vulnerabilitiesTree = Tree(rootTreeNode).apply {
            this.isRootVisible = false
        }

        cut = SnykToolWindowSnykScanListenerLS(
            project,
            snykToolWindowPanel,
            vulnerabilitiesTree,
            rootSecurityIssuesTreeNode,
            rootQualityIssuesTreeNode,
            rootOssIssuesTreeNode,
            rootIacIssuesTreeNode
        )

        pluginSettings().isGlobalIgnoresFeatureEnabled = true
        pluginSettings().openIssuesEnabled = true
        pluginSettings().ignoredIssuesEnabled = true
    }

    override fun tearDown() {
        super.tearDown()
        pluginSettings().isGlobalIgnoresFeatureEnabled = true
        pluginSettings().openIssuesEnabled = true
        pluginSettings().ignoredIssuesEnabled = true
        unmockkAll()
    }

    private fun mockScanIssues(
        isIgnored: Boolean? = false,
        hasAIFix: Boolean? = false,
    ): List<ScanIssue> {
        val issue = ScanIssue(
            id = "id",
            title = "title",
            severity = Severity.CRITICAL.toString(),
            filePath = getTestDataPath(),
            range = Range(),
            additionalData = IssueData(
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
                publicId = "",
                documentation = "",
                lineNumber = "",
                issue = "",
                impact = "",
                resolve = "",
                path = emptyList(),
                references = emptyList(),
                customUIContent = "",
                key = "",
            ),
            isIgnored = isIgnored,
            ignoreDetails = null,
            isNew = false,
            filterableIssueType = ScanIssue.OPEN_SOURCE,
        )
        return listOf(issue)
    }

    private fun mapToLabels(treeNode: DefaultMutableTreeNode): List<String> {
        return treeNode.children().toList().map{ it.toString() }
    }

    fun `test root nodes are created`() {
        TestCase.assertEquals(listOf(" Open Source", " Code Security", " Code Quality", " Configuration"), mapToLabels(rootTreeNode))
    }

    fun `test addInfoTreeNodes adds new tree nodes for non-security if no issues`() {
        cut.addInfoTreeNodes(ScanIssue.OPEN_SOURCE, rootOssIssuesTreeNode, listOf(), 0)
        TestCase.assertEquals(listOf("✅ Congrats! No issues found!"), mapToLabels(rootOssIssuesTreeNode))
    }

    fun `test addInfoTreeNodes adds new tree nodes for code security if no issues`() {
        cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, listOf(), 0)
        TestCase.assertEquals(listOf("✅ Congrats! No issues found!"), mapToLabels(rootSecurityIssuesTreeNode))
    }

    fun `test addInfoTreeNodes adds new tree nodes for non-security if 1 fixable issue`() {
        cut.addInfoTreeNodes(ScanIssue.OPEN_SOURCE, rootOssIssuesTreeNode, mockScanIssues(hasAIFix = true), 1)
        TestCase.assertEquals(
            listOf("✋ 1 issue", "⚡ 1 issue can be fixed automatically"),
            mapToLabels(rootOssIssuesTreeNode)
        )
    }

    fun `test addInfoTreeNodes adds new tree nodes for code security if 1 fixable issue`() {
        cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, mockScanIssues(hasAIFix = true), 1)
        TestCase.assertEquals(listOf("✋ 1 open issue, 0 ignored issues", "⚡ 1 issue can be fixed automatically"), mapToLabels(rootSecurityIssuesTreeNode))
    }

    fun `test addInfoTreeNodes adds new tree nodes for non-security if open issues are hidden`() {
        pluginSettings().openIssuesEnabled = false

        cut.addInfoTreeNodes(ScanIssue.OPEN_SOURCE, rootOssIssuesTreeNode, listOf(), 0)
        TestCase.assertEquals(listOf("Open issues are disabled!", "Adjust your settings to view Open issues."), mapToLabels(rootOssIssuesTreeNode))
    }

    fun `test addInfoTreeNodes adds new tree nodes for code security if open issues are hidden and no ignored issues`() {
        pluginSettings().openIssuesEnabled = false

        cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, listOf(), 0)
        TestCase.assertEquals(listOf("✋ No ignored issues, open issues are disabled", "Adjust your settings to view Open issues."), mapToLabels(rootSecurityIssuesTreeNode))
    }

    fun `test addInfoTreeNodes adds new tree nodes for code security if open issues are hidden and 1 ignored issues`() {
        pluginSettings().openIssuesEnabled = false

        cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, mockScanIssues(isIgnored = true), 0)
        TestCase.assertEquals(listOf("✋ 1 ignored issue, open issues are disabled"), mapToLabels(rootSecurityIssuesTreeNode))
    }

    fun `test addInfoTreeNodes adds new tree nodes for code security if ignored issues are hidden and no open issues`() {
        pluginSettings().ignoredIssuesEnabled = false

        cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, listOf(), 0)
        TestCase.assertEquals(listOf("✅ Congrats! No open issues found!", "Adjust your settings to view Ignored issues."), mapToLabels(rootSecurityIssuesTreeNode))
    }

    fun `test addInfoTreeNodes adds new tree nodes for code security if ignored issues are hidden and 1 fixable issue`() {
        pluginSettings().ignoredIssuesEnabled = false

        cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, mockScanIssues(hasAIFix = true), 1)
        TestCase.assertEquals(listOf("✋ 1 open issue", "⚡ 1 issue can be fixed automatically"), mapToLabels(rootSecurityIssuesTreeNode))
    }
}
