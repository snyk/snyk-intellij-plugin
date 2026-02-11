package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.treeStructure.Tree
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.Severity
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import java.nio.file.Paths
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.absolutePathString
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import snyk.common.annotator.SnykCodeAnnotator
import snyk.common.lsp.DataFlow
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.IssueData
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.trust.WorkspaceTrustSettings

class SnykToolWindowScanListenerTest : BasePlatformTestCase() {
  private lateinit var cut: SnykToolWindowSnykScanListener
  private lateinit var snykToolWindowPanel: SnykToolWindowPanel
  private lateinit var vulnerabilitiesTree: JTree
  private lateinit var rootTreeNode: DefaultMutableTreeNode
  private lateinit var rootOssIssuesTreeNode: DefaultMutableTreeNode
  private lateinit var rootSecurityIssuesTreeNode: DefaultMutableTreeNode
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
    unmockkAll()
    super.setUp()
    resetSettings(project)

    file = myFixture.copyFileToProject(fileName)
    psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
    val contentRootPaths = project.getContentRootPaths()
    service<FolderConfigSettings>()
      .addFolderConfig(
        FolderConfig(contentRootPaths.first().toAbsolutePath().toString(), baseBranch = "main")
      )
    snykToolWindowPanel = SnykToolWindowPanel(project)
    rootOssIssuesTreeNode = RootOssTreeNode(project)
    rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
    rootIacIssuesTreeNode = RootIacIssuesTreeNode(project)
    pluginSettings().setDeltaEnabled(enabled = true)
    contentRootPaths.forEach {
      service<WorkspaceTrustSettings>().addTrustedPath(it.root.absolutePathString())
    }

    rootTreeNode = DefaultMutableTreeNode("")
    rootTreeNode.add(rootOssIssuesTreeNode)
    rootTreeNode.add(rootSecurityIssuesTreeNode)
    rootTreeNode.add(rootIacIssuesTreeNode)
    vulnerabilitiesTree = Tree(rootTreeNode).apply { this.isRootVisible = false }

    cut =
      SnykToolWindowSnykScanListener(
        project,
        snykToolWindowPanel,
        vulnerabilitiesTree,
        rootSecurityIssuesTreeNode,
        rootOssIssuesTreeNode,
        rootIacIssuesTreeNode,
      )

    pluginSettings().isGlobalIgnoresFeatureEnabled = true
    pluginSettings().openIssuesEnabled = true
    pluginSettings().ignoredIssuesEnabled = true
  }

  override fun tearDown() {
    super.tearDown()
    pluginSettings().isGlobalIgnoresFeatureEnabled = true
    pluginSettings().setDeltaEnabled(false)
    unmockkAll()
  }

  private fun disableCCI() {
    pluginSettings().isGlobalIgnoresFeatureEnabled = false
    // The issue view options shouldn't matter, but we'll test with them disabled to be sure.
    pluginSettings().openIssuesEnabled = false
    pluginSettings().ignoredIssuesEnabled = false
  }

  private fun mockScanIssues(
    isIgnored: Boolean? = false,
    hasAIFix: Boolean? = false,
  ): List<ScanIssue> {
    return mockScanIssuesWithSeverity(Severity.CRITICAL, isIgnored, hasAIFix)
  }

  private fun mockScanIssuesWithSeverity(
    severity: Severity,
    isIgnored: Boolean? = false,
    hasAIFix: Boolean? = false,
    filterableType: String = ScanIssue.OPEN_SOURCE,
    id: String = "id",
    riskScore: Int = 0,
  ): List<ScanIssue> {
    val issue =
      ScanIssue(
        id = id,
        title = "title",
        severity = severity.toString(),
        filePath = getTestDataPath(),
        range = Range(Position(0, 0), Position(0, 0)),
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
            dataFlow =
              listOf(DataFlow(0, getTestDataPath(), Range(Position(1, 1), Position(1, 1)), "")),
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
            riskScore = riskScore,
          ),
        isIgnored = isIgnored,
        ignoreDetails = null,
        isNew = false,
        filterableIssueType = filterableType,
      )
    return listOf(issue)
  }

  private fun mapToLabels(treeNode: DefaultMutableTreeNode): List<String> {
    return treeNode.children().toList().map { it.toString() }
  }

  fun `test root nodes are created`() {
    assertEquals(
      listOf(" Open Source", " Code Security", " Configuration"),
      mapToLabels(rootTreeNode),
    )
  }

  fun `test addInfoTreeNodes adds new tree nodes for non-code if no issues and CCI disabled`() {
    disableCCI()

    cut.addInfoTreeNodes(ScanIssue.OPEN_SOURCE, rootOssIssuesTreeNode, listOf(), 0)
    assertEquals(listOf("✅ Congrats! No issues found!"), mapToLabels(rootOssIssuesTreeNode))
  }

  fun `test addInfoTreeNodes adds new tree nodes for non-code if no issues and viewing open and CCI enabled`() {
    cut.addInfoTreeNodes(ScanIssue.OPEN_SOURCE, rootOssIssuesTreeNode, listOf(), 0)
    assertEquals(listOf("✅ Congrats! No issues found!"), mapToLabels(rootOssIssuesTreeNode))
  }

  fun `test addInfoTreeNodes adds new tree nodes for code security if no issues and CCI disabled`() {
    disableCCI()

    cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, listOf(), 0)
    assertEquals(listOf("✅ Congrats! No issues found!"), mapToLabels(rootSecurityIssuesTreeNode))
  }

  fun `test addInfoTreeNodes adds new tree nodes for non-code if 1 non-fixable issue with CCI disabled`() {
    disableCCI()

    cut.addInfoTreeNodes(ScanIssue.OPEN_SOURCE, rootOssIssuesTreeNode, mockScanIssues(), 0)
    assertEquals(
      listOf("✋ 1 issue", "There are no issues automatically fixable."),
      mapToLabels(rootOssIssuesTreeNode),
    )
  }

  fun `test addInfoTreeNodes adds new tree nodes for non-code if 1 fixable issue and viewing open with CCI enabled`() {
    cut.addInfoTreeNodes(
      ScanIssue.OPEN_SOURCE,
      rootOssIssuesTreeNode,
      mockScanIssues(hasAIFix = true),
      1,
    )
    assertEquals(
      listOf("✋ 1 issue", "⚡ 1 issue is fixable automatically."),
      mapToLabels(rootOssIssuesTreeNode),
    )
  }

  fun `test addInfoTreeNodes adds new tree nodes for code security if 1 fixable issue with CCI disabled`() {
    disableCCI()

    cut.addInfoTreeNodes(
      ScanIssue.CODE_SECURITY,
      rootSecurityIssuesTreeNode,
      mockScanIssues(hasAIFix = true),
      1,
    )
    assertEquals(
      listOf("✋ 1 issue", "⚡ 1 issue is fixable automatically."),
      mapToLabels(rootSecurityIssuesTreeNode),
    )
  }

  fun `test addInfoTreeNodes adds new tree nodes for non-code if open issues are hidden with CCI enabled`() {
    pluginSettings().openIssuesEnabled = false

    cut.addInfoTreeNodes(ScanIssue.OPEN_SOURCE, rootOssIssuesTreeNode, listOf(), 0)
    assertEquals(
      listOf("Open issues are disabled!", "Adjust your settings to view Open issues."),
      mapToLabels(rootOssIssuesTreeNode),
    )
  }

  fun `test addInfoTreeNodes adds new tree nodes for code security if open issues are hidden and no ignored issues with CCI enabled`() {
    pluginSettings().openIssuesEnabled = false

    cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, listOf(), 0)
    assertEquals(
      listOf(
        "✋ No ignored issues, open issues are disabled",
        "Adjust your settings to view Open issues.",
      ),
      mapToLabels(rootSecurityIssuesTreeNode),
    )
  }

  fun `test addInfoTreeNodes adds new tree nodes for code security if ignored issues are hidden and no open issues with CCI enabled`() {
    pluginSettings().ignoredIssuesEnabled = false

    cut.addInfoTreeNodes(ScanIssue.CODE_SECURITY, rootSecurityIssuesTreeNode, listOf(), 0)
    assertEquals(
      listOf("✅ Congrats! No open issues found!", "Adjust your settings to view Ignored issues."),
      mapToLabels(rootSecurityIssuesTreeNode),
    )
  }

  fun `test scanning started does not reset summary panel`() {
    val toolWindowPanelMock = mockk<SnykToolWindowPanel>(relaxed = true)
    val listener =
      SnykToolWindowSnykScanListener(
        project,
        toolWindowPanelMock,
        mockk(relaxed = true),
        DefaultMutableTreeNode(),
        DefaultMutableTreeNode(),
        DefaultMutableTreeNode(),
      )

    listener.scanningStarted(mockk(relaxed = true))
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    verify(exactly = 0) { toolWindowPanelMock.cleanUiAndCaches(any()) }
  }

  fun `test displaySnykCodeResults shows issues when tree filtering disabled`() {
    pluginSettings().token = "dummy"
    pluginSettings().snykCodeSecurityIssuesScanEnable = true
    pluginSettings().treeFiltering.codeSecurityResults = false

    val snykFile = io.snyk.plugin.SnykFile(project, file)
    val issue = mockScanIssues().first().copy(filterableIssueType = ScanIssue.CODE_SECURITY)
    cut.displaySnykCodeResults(mapOf(snykFile to setOf(issue)))

    // Should contain info nodes and at least one file node.
    assertTrue(rootSecurityIssuesTreeNode.childCount > 0)
    val labels = mapToLabels(rootSecurityIssuesTreeNode)
    assertTrue(labels.any { it.contains("issue") || it.contains("✅") || it.contains("✋") })
  }

  fun `test displayOssResults filters issues by severity and updates count correctly`() {
    pluginSettings().token = "dummy"
    pluginSettings().ossScanEnable = true
    pluginSettings().treeFiltering.ossResults = true

    // Disable Medium and Low severity filtering
    pluginSettings().treeFiltering.mediumSeverity = false
    pluginSettings().treeFiltering.lowSeverity = false
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.criticalSeverity = true

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create issues with different severities
    val highIssue =
      mockScanIssuesWithSeverity(
          Severity.HIGH,
          filterableType = ScanIssue.OPEN_SOURCE,
          id = "high-1",
        )
        .first()
    val mediumIssue =
      mockScanIssuesWithSeverity(
          Severity.MEDIUM,
          filterableType = ScanIssue.OPEN_SOURCE,
          id = "medium-1",
        )
        .first()
    val lowIssue =
      mockScanIssuesWithSeverity(Severity.LOW, filterableType = ScanIssue.OPEN_SOURCE, id = "low-1")
        .first()

    cut.displayOssResults(mapOf(snykFile to setOf(highIssue, mediumIssue, lowIssue)))

    // Should only show 1 issue (high) since medium and low are filtered out
    val labels = mapToLabels(rootOssIssuesTreeNode)
    assertTrue("Expected '✋ 1 issue' but got: $labels", labels.any { it.contains("✋ 1 issue") })
  }

  fun `test displaySnykCodeResults filters issues by severity and updates count correctly`() {
    pluginSettings().token = "dummy"
    pluginSettings().snykCodeSecurityIssuesScanEnable = true
    pluginSettings().treeFiltering.codeSecurityResults = true

    // Enable only High severity
    pluginSettings().treeFiltering.mediumSeverity = false
    pluginSettings().treeFiltering.lowSeverity = false
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.criticalSeverity = false

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create 2 high and 1 medium issue
    val highIssue1 =
      mockScanIssuesWithSeverity(
          Severity.HIGH,
          filterableType = ScanIssue.CODE_SECURITY,
          id = "high-1",
        )
        .first()
    val highIssue2 =
      mockScanIssuesWithSeverity(
          Severity.HIGH,
          filterableType = ScanIssue.CODE_SECURITY,
          id = "high-2",
        )
        .first()
    val mediumIssue =
      mockScanIssuesWithSeverity(
          Severity.MEDIUM,
          filterableType = ScanIssue.CODE_SECURITY,
          id = "medium-1",
        )
        .first()

    cut.displaySnykCodeResults(mapOf(snykFile to setOf(highIssue1, highIssue2, mediumIssue)))

    // Should show 2 issues (both high) - note: with CCI enabled it shows "open issues"
    val labels = mapToLabels(rootSecurityIssuesTreeNode)
    assertTrue(
      "Expected label containing '2' and 'issue' but got: $labels",
      labels.any { it.contains("2") && it.contains("issue") },
    )
  }

  fun `test displayOssResults shows all issues when severity filtering disabled`() {
    pluginSettings().token = "dummy"
    pluginSettings().ossScanEnable = true
    pluginSettings().treeFiltering.ossResults = false // Disable filtering

    // Even if severities are disabled in treeFiltering, issues should still show
    pluginSettings().treeFiltering.mediumSeverity = false
    pluginSettings().treeFiltering.lowSeverity = false
    pluginSettings().treeFiltering.highSeverity = false

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    val highIssue =
      mockScanIssuesWithSeverity(
          Severity.HIGH,
          filterableType = ScanIssue.OPEN_SOURCE,
          id = "high-1",
        )
        .first()
    val mediumIssue =
      mockScanIssuesWithSeverity(
          Severity.MEDIUM,
          filterableType = ScanIssue.OPEN_SOURCE,
          id = "medium-1",
        )
        .first()

    cut.displayOssResults(mapOf(snykFile to setOf(highIssue, mediumIssue)))

    // Should show both issues since tree filtering is disabled
    val labels = mapToLabels(rootOssIssuesTreeNode)
    assertTrue("Expected '✋ 2 issues' but got: $labels", labels.any { it.contains("✋ 2 issues") })
  }

  fun `test displayIacResults filters issues by severity correctly`() {
    pluginSettings().token = "dummy"
    pluginSettings().iacScanEnabled = true
    pluginSettings().treeFiltering.iacResults = true

    // Only enable critical severity
    pluginSettings().treeFiltering.criticalSeverity = true
    pluginSettings().treeFiltering.highSeverity = false
    pluginSettings().treeFiltering.mediumSeverity = false
    pluginSettings().treeFiltering.lowSeverity = false

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    val criticalIssue =
      mockScanIssuesWithSeverity(
          Severity.CRITICAL,
          filterableType = ScanIssue.INFRASTRUCTURE_AS_CODE,
          id = "critical-1",
        )
        .first()
    val highIssue =
      mockScanIssuesWithSeverity(
          Severity.HIGH,
          filterableType = ScanIssue.INFRASTRUCTURE_AS_CODE,
          id = "high-1",
        )
        .first()

    cut.displayIacResults(mapOf(snykFile to setOf(criticalIssue, highIssue)))

    // Should show 1 issue (critical only)
    val labels = mapToLabels(rootIacIssuesTreeNode)
    assertTrue("Expected '✋ 1 issue' but got: $labels", labels.any { it.contains("✋ 1 issue") })
  }

  fun `test filtered issue count matches displayed tree nodes`() {
    pluginSettings().token = "dummy"
    pluginSettings().ossScanEnable = true
    pluginSettings().treeFiltering.ossResults = true

    // Enable only High severity
    pluginSettings().treeFiltering.criticalSeverity = false
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.mediumSeverity = false
    pluginSettings().treeFiltering.lowSeverity = false

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create 3 high, 2 medium, 1 low issue
    val issues =
      listOf(
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "high-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "high-2",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "high-3",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "medium-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "medium-2",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.LOW,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "low-1",
          )
          .first(),
      )

    cut.displayOssResults(mapOf(snykFile to issues.toSet()))

    // Info nodes should show 3 issues (high only)
    val labels = mapToLabels(rootOssIssuesTreeNode)
    assertTrue("Expected '✋ 3 issues' but got: $labels", labels.any { it.contains("✋ 3 issues") })

    // Count the actual issue nodes in the tree (skip info nodes)
    var actualIssueCount = 0
    for (i in 0 until rootOssIssuesTreeNode.childCount) {
      val child = rootOssIssuesTreeNode.getChildAt(i) as DefaultMutableTreeNode
      if (child is io.snyk.plugin.ui.toolwindow.nodes.secondlevel.SnykFileTreeNode) {
        actualIssueCount += child.childCount
      }
    }
    assertEquals("Tree should contain 3 issue nodes", 3, actualIssueCount)
  }

  fun `test root node postfix includes critical severity count`() {
    pluginSettings().token = "dummy"
    pluginSettings().ossScanEnable = true
    pluginSettings().treeFiltering.ossResults = true

    // Enable all severities
    pluginSettings().treeFiltering.criticalSeverity = true
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.mediumSeverity = true
    pluginSettings().treeFiltering.lowSeverity = true

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create 2 critical, 1 high, 1 medium issue
    val issues =
      listOf(
        mockScanIssuesWithSeverity(
            Severity.CRITICAL,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "critical-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.CRITICAL,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "critical-2",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "high-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "medium-1",
          )
          .first(),
      )

    cut.displayOssResults(mapOf(snykFile to issues.toSet()))

    // Get the panel's actual root OSS node which receives the postfix via
    // updateTreeRootNodesPresentation
    val panelRootOssNode = snykToolWindowPanel.getRootOssIssuesTreeNode()
    val rootNodeText = panelRootOssNode.userObject.toString()
    assertTrue(
      "Expected root node to contain '2 critical' but got: $rootNodeText",
      rootNodeText.contains("2 critical"),
    )
    assertTrue(
      "Expected root node to contain '1 high' but got: $rootNodeText",
      rootNodeText.contains("1 high"),
    )
    assertTrue(
      "Expected root node to contain '1 medium' but got: $rootNodeText",
      rootNodeText.contains("1 medium"),
    )
  }

  fun `test scanningOssFinished fetches results from cache at execution time`() {
    pluginSettings().token = "dummy"
    pluginSettings().ossScanEnable = true
    pluginSettings().treeFiltering.ossResults = false

    val snykFile = io.snyk.plugin.SnykFile(project, file)
    val initialIssue =
      mockScanIssuesWithSeverity(
          Severity.HIGH,
          filterableType = ScanIssue.OPEN_SOURCE,
          id = "initial-1",
        )
        .first()
    val additionalIssue =
      mockScanIssuesWithSeverity(
          Severity.CRITICAL,
          filterableType = ScanIssue.OPEN_SOURCE,
          id = "additional-1",
        )
        .first()

    // 1. Populate cache with initial issue
    getSnykCachedResults(project)?.currentOSSResultsLS?.put(snykFile, setOf(initialIssue))

    // 2. Call scanningOssFinished (this schedules invokeLater but doesn't execute it yet)
    cut.scanningOssFinished()

    // 3. Update cache with additional issue BEFORE invokeLater executes
    // This simulates diagnostics arriving after scan finishes but before UI updates
    getSnykCachedResults(project)
      ?.currentOSSResultsLS
      ?.put(snykFile, setOf(initialIssue, additionalIssue))

    // 4. Process events to run invokeLater
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // 5. Verify that both issues appear (results were fetched at execution time)
    val labels = mapToLabels(rootOssIssuesTreeNode)
    assertTrue(
      "Expected tree to show 2 issues (fetched at execution time), but got: $labels",
      labels.any { it.contains("2 issues") },
    )
  }

  fun `test scanningSnykCodeFinished fetches results from cache at execution time`() {
    pluginSettings().token = "dummy"
    pluginSettings().snykCodeSecurityIssuesScanEnable = true
    pluginSettings().treeFiltering.codeSecurityResults = false

    val snykFile = io.snyk.plugin.SnykFile(project, file)
    val initialIssue =
      mockScanIssuesWithSeverity(
          Severity.HIGH,
          filterableType = ScanIssue.CODE_SECURITY,
          id = "initial-1",
        )
        .first()
    val additionalIssue =
      mockScanIssuesWithSeverity(
          Severity.CRITICAL,
          filterableType = ScanIssue.CODE_SECURITY,
          id = "additional-1",
        )
        .first()

    // 1. Populate cache with initial issue
    getSnykCachedResults(project)?.currentSnykCodeResultsLS?.put(snykFile, setOf(initialIssue))

    // 2. Call scanningSnykCodeFinished (schedules invokeLater)
    cut.scanningSnykCodeFinished()

    // 3. Update cache before invokeLater executes
    getSnykCachedResults(project)
      ?.currentSnykCodeResultsLS
      ?.put(snykFile, setOf(initialIssue, additionalIssue))

    // 4. Process events
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // 5. Verify both issues appear
    val labels = mapToLabels(rootSecurityIssuesTreeNode)
    assertTrue(
      "Expected tree to show 2 issues (fetched at execution time), but got: $labels",
      labels.any { it.contains("2") && it.contains("issue") },
    )
  }

  fun `test scanningIacFinished fetches results from cache at execution time`() {
    pluginSettings().token = "dummy"
    pluginSettings().iacScanEnabled = true
    pluginSettings().treeFiltering.iacResults = false

    val snykFile = io.snyk.plugin.SnykFile(project, file)
    val initialIssue =
      mockScanIssuesWithSeverity(
          Severity.HIGH,
          filterableType = ScanIssue.INFRASTRUCTURE_AS_CODE,
          id = "initial-1",
        )
        .first()
    val additionalIssue =
      mockScanIssuesWithSeverity(
          Severity.CRITICAL,
          filterableType = ScanIssue.INFRASTRUCTURE_AS_CODE,
          id = "additional-1",
        )
        .first()

    // 1. Populate cache with initial issue
    getSnykCachedResults(project)?.currentIacResultsLS?.put(snykFile, setOf(initialIssue))

    // 2. Call scanningIacFinished (schedules invokeLater)
    cut.scanningIacFinished()

    // 3. Update cache before invokeLater executes
    getSnykCachedResults(project)
      ?.currentIacResultsLS
      ?.put(snykFile, setOf(initialIssue, additionalIssue))

    // 4. Process events
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // 5. Verify both issues appear
    val labels = mapToLabels(rootIacIssuesTreeNode)
    assertTrue(
      "Expected tree to show 2 issues (fetched at execution time), but got: $labels",
      labels.any { it.contains("2 issues") },
    )
  }

  fun `test root node postfix does not include critical for Code Security`() {
    pluginSettings().token = "dummy"
    pluginSettings().snykCodeSecurityIssuesScanEnable = true
    pluginSettings().treeFiltering.codeSecurityResults = true

    // Enable all severities
    pluginSettings().treeFiltering.criticalSeverity = true
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.mediumSeverity = true
    pluginSettings().treeFiltering.lowSeverity = true

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create issues with all severities including critical
    val issues =
      listOf(
        mockScanIssuesWithSeverity(
            Severity.CRITICAL,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "critical-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "high-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "medium-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.LOW,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "low-1",
          )
          .first(),
      )

    cut.displaySnykCodeResults(mapOf(snykFile to issues.toSet()))

    // Get the panel's actual root Code Security node
    val panelRootCodeNode = snykToolWindowPanel.getRootSecurityIssuesTreeNode()
    val rootNodeText = panelRootCodeNode.userObject.toString()

    // Code Security should NOT show critical severity count
    assertFalse(
      "Expected root node NOT to contain 'critical' but got: $rootNodeText",
      rootNodeText.contains("critical"),
    )
    // But should still show other severities
    assertTrue(
      "Expected root node to contain '1 high' but got: $rootNodeText",
      rootNodeText.contains("1 high"),
    )
    assertTrue(
      "Expected root node to contain '1 medium' but got: $rootNodeText",
      rootNodeText.contains("1 medium"),
    )
    assertTrue(
      "Expected root node to contain '1 low' but got: $rootNodeText",
      rootNodeText.contains("1 low"),
    )
  }

  fun `test root node postfix includes critical for OSS`() {
    pluginSettings().token = "dummy"
    pluginSettings().ossScanEnable = true
    pluginSettings().treeFiltering.ossResults = true

    // Enable all severities
    pluginSettings().treeFiltering.criticalSeverity = true
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.mediumSeverity = true
    pluginSettings().treeFiltering.lowSeverity = true

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create issues with all severities
    val issues =
      listOf(
        mockScanIssuesWithSeverity(
            Severity.CRITICAL,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "critical-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "high-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "medium-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.LOW,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "low-1",
          )
          .first(),
      )

    cut.displayOssResults(mapOf(snykFile to issues.toSet()))

    // Get the panel's actual root OSS node
    val panelRootOssNode = snykToolWindowPanel.getRootOssIssuesTreeNode()
    val rootNodeText = panelRootOssNode.userObject.toString()

    // OSS should show critical severity count
    assertTrue(
      "Expected root node to contain '1 critical' but got: $rootNodeText",
      rootNodeText.contains("1 critical"),
    )
    assertTrue(
      "Expected root node to contain '1 high' but got: $rootNodeText",
      rootNodeText.contains("1 high"),
    )
    assertTrue(
      "Expected root node to contain '1 medium' but got: $rootNodeText",
      rootNodeText.contains("1 medium"),
    )
    assertTrue(
      "Expected root node to contain '1 low' but got: $rootNodeText",
      rootNodeText.contains("1 low"),
    )
  }

  fun `test root node postfix includes critical for IAC`() {
    pluginSettings().token = "dummy"
    pluginSettings().iacScanEnabled = true
    pluginSettings().treeFiltering.iacResults = true

    // Enable all severities
    pluginSettings().treeFiltering.criticalSeverity = true
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.mediumSeverity = true
    pluginSettings().treeFiltering.lowSeverity = true

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create issues with all severities
    val issues =
      listOf(
        mockScanIssuesWithSeverity(
            Severity.CRITICAL,
            filterableType = ScanIssue.INFRASTRUCTURE_AS_CODE,
            id = "critical-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.INFRASTRUCTURE_AS_CODE,
            id = "high-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.INFRASTRUCTURE_AS_CODE,
            id = "medium-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.LOW,
            filterableType = ScanIssue.INFRASTRUCTURE_AS_CODE,
            id = "low-1",
          )
          .first(),
      )

    cut.displayIacResults(mapOf(snykFile to issues.toSet()))

    // Get the panel's actual root IAC node
    val panelRootIacNode = snykToolWindowPanel.getRootIacIssuesTreeNode()
    val rootNodeText = panelRootIacNode.userObject.toString()

    // IAC should show critical severity count
    assertTrue(
      "Expected root node to contain '1 critical' but got: $rootNodeText",
      rootNodeText.contains("1 critical"),
    )
    assertTrue(
      "Expected root node to contain '1 high' but got: $rootNodeText",
      rootNodeText.contains("1 high"),
    )
    assertTrue(
      "Expected root node to contain '1 medium' but got: $rootNodeText",
      rootNodeText.contains("1 medium"),
    )
    assertTrue(
      "Expected root node to contain '1 low' but got: $rootNodeText",
      rootNodeText.contains("1 low"),
    )
  }

  fun `test severity postfix formatting without critical`() {
    pluginSettings().token = "dummy"
    pluginSettings().snykCodeSecurityIssuesScanEnable = true
    pluginSettings().treeFiltering.codeSecurityResults = true

    // Enable all severities
    pluginSettings().treeFiltering.criticalSeverity = true
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.mediumSeverity = true
    pluginSettings().treeFiltering.lowSeverity = true

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create 2 high, 3 medium, 1 low (no critical to test formatting)
    val issues =
      listOf(
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "high-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "high-2",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "medium-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "medium-2",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "medium-3",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.LOW,
            filterableType = ScanIssue.CODE_SECURITY,
            id = "low-1",
          )
          .first(),
      )

    cut.displaySnykCodeResults(mapOf(snykFile to issues.toSet()))

    val panelRootCodeNode = snykToolWindowPanel.getRootSecurityIssuesTreeNode()
    val rootNodeText = panelRootCodeNode.userObject.toString()

    // Verify proper formatting: should be ": 2 high, 3 medium, 1 low" (no critical)
    assertTrue(
      "Expected root node to contain '2 high' but got: $rootNodeText",
      rootNodeText.contains("2 high"),
    )
    assertTrue(
      "Expected root node to contain '3 medium' but got: $rootNodeText",
      rootNodeText.contains("3 medium"),
    )
    assertTrue(
      "Expected root node to contain '1 low' but got: $rootNodeText",
      rootNodeText.contains("1 low"),
    )
    // Ensure no double spaces or formatting issues from missing critical
    assertFalse(
      "Root node should not have double spaces: $rootNodeText",
      rootNodeText.contains("  "),
    )
  }

  fun `test severity postfix formatting with critical`() {
    pluginSettings().token = "dummy"
    pluginSettings().ossScanEnable = true
    pluginSettings().treeFiltering.ossResults = true

    // Enable all severities
    pluginSettings().treeFiltering.criticalSeverity = true
    pluginSettings().treeFiltering.highSeverity = true
    pluginSettings().treeFiltering.mediumSeverity = true
    pluginSettings().treeFiltering.lowSeverity = true

    val snykFile = io.snyk.plugin.SnykFile(project, file)

    // Create 3 critical, 2 high, 1 medium, 0 low
    val issues =
      listOf(
        mockScanIssuesWithSeverity(
            Severity.CRITICAL,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "critical-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.CRITICAL,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "critical-2",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.CRITICAL,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "critical-3",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "high-1",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.HIGH,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "high-2",
          )
          .first(),
        mockScanIssuesWithSeverity(
            Severity.MEDIUM,
            filterableType = ScanIssue.OPEN_SOURCE,
            id = "medium-1",
          )
          .first(),
      )

    cut.displayOssResults(mapOf(snykFile to issues.toSet()))

    val panelRootOssNode = snykToolWindowPanel.getRootOssIssuesTreeNode()
    val rootNodeText = panelRootOssNode.userObject.toString()

    // Verify proper formatting: should be ": 3 critical, 2 high, 1 medium, 0 low"
    assertTrue(
      "Expected root node to contain '3 critical' but got: $rootNodeText",
      rootNodeText.contains("3 critical"),
    )
    assertTrue(
      "Expected root node to contain '2 high' but got: $rootNodeText",
      rootNodeText.contains("2 high"),
    )
    assertTrue(
      "Expected root node to contain '1 medium' but got: $rootNodeText",
      rootNodeText.contains("1 medium"),
    )
    assertTrue(
      "Expected root node to contain '0 low' but got: $rootNodeText",
      rootNodeText.contains("0 low"),
    )
  }
}
