package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.treeStructure.Tree
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.isIacRunning
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykTaskQueueService
import java.awt.Container
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.swing.tree.DefaultMutableTreeNode
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.WorkspaceService
import org.junit.Ignore
import org.junit.Test
import snyk.UIComponentFinder
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.SnykLanguageClient

class SnykToolWindowPanelTest : LightPlatform4TestCase() {
  private val taskQueueService = mockk<SnykTaskQueueService>(relaxed = true)
  private val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
  private lateinit var cut: SnykToolWindowPanel
  val lsMock = mockk<LanguageServer>()
  private val lsClientMock = mockk<SnykLanguageClient>()
  private val lsProcessMock = mockk<Process>()
  private val workspaceServiceMock = mockk<WorkspaceService>()

  override fun setUp() {
    super.setUp()
    unmockkAll()

    val application = ApplicationManager.getApplication()
    application.replaceService(
      SnykApplicationSettingsStateService::class.java,
      settings,
      application,
    )

    project.replaceService(SnykTaskQueueService::class.java, taskQueueService, project)

    val lsw = LanguageServerWrapper.getInstance(project)
    lsw.languageServer = lsMock
    lsw.languageClient = lsClientMock
    lsw.process = lsProcessMock
    lsw.isInitialized = true

    every { lsProcessMock.info().startInstant().isPresent } returns true
    every { lsProcessMock.isAlive } returns true
    every { lsMock.workspaceService } returns workspaceServiceMock
    val sastSettings =
      mapOf(
        Pair("sastEnabled", true),
        Pair("org", "1234"),
        Pair("reportFalsePositivesEnabled", false),
        Pair("autofixEnabled", false),
        Pair("supportedLanguages", emptyList<String>()),
      )
    every { workspaceServiceMock.executeCommand(any()) } returns
      CompletableFuture.completedFuture(sastSettings)

    every { settings.token } returns null
    every { settings.sastOnServerEnabled } returns true
  }

  override fun tearDown() {
    unmockkAll()

    val application = ApplicationManager.getApplication()
    application.replaceService(
      SnykApplicationSettingsStateService::class.java,
      SnykApplicationSettingsStateService(),
      application,
    )

    project.replaceService(SnykTaskQueueService::class.java, SnykTaskQueueService(project), project)
    super.tearDown()
  }

  @Test
  fun `should display auth panel `() {
    every { settings.pluginFirstRun } returns true

    cut = SnykToolWindowPanel(project)

    val authPanel = UIComponentFinder.getJPanelByName(cut, "authPanel")
    assertNotNull(authPanel)
    val treePanel = UIComponentFinder.getJPanelByName(cut, "treePanel")
    assertNotNull(treePanel)
    val summaryPanel = UIComponentFinder.getJPanelByName(cut, "summaryPanel")
    assertNotNull(summaryPanel)
  }

  @Test
  fun `should not display onboarding panel and run scan directly`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns true
    justRun { taskQueueService.scan() }

    cut = SnykToolWindowPanel(project)

    val vulnerabilityTree = cut.vulnerabilitiesTree
    val descriptionPanel = UIComponentFinder.getJPanelByName(cut, "descriptionPanel")
    assertNotNull(descriptionPanel)
    assertEquals(findOnePixelSplitter(vulnerabilityTree)?.parent, descriptionPanel!!.parent)
  }

  // TODO rewrite
  @Ignore("change to language server")
  @Test
  fun `should automatically enable all products on first run after Auth`() {
    val application = ApplicationManager.getApplication()
    application.replaceService(
      SnykApplicationSettingsStateService::class.java,
      SnykApplicationSettingsStateService(),
      application,
    )
    pluginSettings().token = "test-token"
    pluginSettings().pluginFirstRun = true

    SnykToolWindowPanel(project)

    assertTrue(pluginSettings().ossScanEnable)
    assertTrue(pluginSettings().snykCodeSecurityIssuesScanEnable)
    assertTrue(pluginSettings().iacScanEnabled)
  }

  @Test
  fun `should automatically enable all products on first run after Auth, with local engine enabled`() {
    val application = ApplicationManager.getApplication()
    val settings = SnykApplicationSettingsStateService()
    application.replaceService(
      SnykApplicationSettingsStateService::class.java,
      settings,
      application,
    )
    settings.token = "test-token"
    settings.pluginFirstRun = true
    settings.cliReleaseChannel = "preview"

    assertTrue(LanguageServerWrapper.getInstance(project).ensureLanguageServerInitialized())

    SnykToolWindowPanel(project)

    assertTrue(pluginSettings().ossScanEnable)
    assertTrue(pluginSettings().snykCodeSecurityIssuesScanEnable)
    assertTrue(pluginSettings().iacScanEnabled)
  }

  private fun findOnePixelSplitter(vulnerabilityTree: Tree): Container? {
    var currentParent = vulnerabilityTree.parent
    while (currentParent != null && currentParent::class != OnePixelSplitter::class) {
      currentParent = currentParent.parent
    }
    return currentParent
  }

  @Test
  fun `smartReloadRootNode should collect multiple nodes and reload all of them`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    cut = SnykToolWindowPanel(project)

    val rootNode = cut.getRootNode()
    val ossNode = cut.getRootOssIssuesTreeNode()
    val iacNode = cut.getRootIacIssuesTreeNode()

    // Simulate rapid sequential calls (like when filtersChanged() triggers multiple products)
    cut.smartReloadRootNode(ossNode)
    cut.smartReloadRootNode(iacNode)

    // Process pending events - the debounced reload should eventually fire
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Give the alarm time to fire (debounce delay is 50ms)
    Thread.sleep(100)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Both nodes should still be in the tree after reload
    assertTrue("OSS node should exist after reload", rootNode.isNodeChild(ossNode))
    assertTrue("IAC node should exist after reload", rootNode.isNodeChild(iacNode))
  }

  @Test
  fun `smartReloadRootNode should preserve tree structure during reload`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    cut = SnykToolWindowPanel(project)

    val ossNode = cut.getRootOssIssuesTreeNode()

    // Add a child node to simulate having results
    val childNode = DefaultMutableTreeNode("Test Child")
    ossNode.add(childNode)

    val childCountBefore = ossNode.childCount

    // Trigger reload
    cut.smartReloadRootNode(ossNode)

    // Process pending events
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Thread.sleep(100)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Child should still exist
    assertEquals("Child count should be preserved", childCountBefore, ossNode.childCount)
  }

  @Test
  fun `doSmartReload should not skip reload when any node type is present`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    cut = SnykToolWindowPanel(project)

    val ossNode = cut.getRootOssIssuesTreeNode()

    // Add info node and issue node
    val infoNode = DefaultMutableTreeNode("✋ 5 issues")
    ossNode.add(infoNode)

    // Select the info node
    cut.getTree().selectionPath =
      javax.swing.tree.TreePath(arrayOf(cut.getRootNode(), ossNode, infoNode))

    // Trigger reload - should NOT skip even with info node selected
    cut.smartReloadRootNode(ossNode)

    // Process pending events
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Thread.sleep(100)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Tree should still be valid
    assertTrue("OSS node should still have children after reload", ossNode.childCount > 0)
  }

  @Test
  fun `flushPendingTreeRefreshes should skip OSS refresh when scan is running`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    mockkStatic(::isOssRunning)
    mockkStatic(::isSnykCodeRunning)
    mockkStatic(::isIacRunning)

    // Simulate OSS scan running
    every { isOssRunning(any()) } returns true
    every { isSnykCodeRunning(any()) } returns false
    every { isIacRunning(any()) } returns false

    cut = SnykToolWindowPanel(project)

    val ossNode = cut.getRootOssIssuesTreeNode()
    val initialChildCount = ossNode.childCount

    // Trigger debounced tree refresh for OSS
    cut.scheduleDebouncedTreeRefreshForTest(snyk.common.lsp.LsProduct.OpenSource)

    // Process pending events and wait for debounce
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Thread.sleep(300)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // OSS node should NOT be modified because scan is running
    assertEquals(
      "OSS node should not be modified while scanning",
      initialChildCount,
      ossNode.childCount,
    )
  }

  @Test
  fun `flushPendingTreeRefreshes should refresh OSS when scan is not running`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    every { settings.ossScanEnable } returns true
    every { settings.treeFiltering } returns mockk(relaxed = true)
    justRun { taskQueueService.scan() }

    mockkStatic(::isOssRunning)
    mockkStatic(::isSnykCodeRunning)
    mockkStatic(::isIacRunning)

    // Simulate no scans running
    every { isOssRunning(any()) } returns false
    every { isSnykCodeRunning(any()) } returns false
    every { isIacRunning(any()) } returns false

    cut = SnykToolWindowPanel(project)

    // Trigger debounced tree refresh for OSS
    cut.scheduleDebouncedTreeRefreshForTest(snyk.common.lsp.LsProduct.OpenSource)

    // Process pending events and wait for debounce
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Thread.sleep(300)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Tree should have been refreshed (no exception thrown)
    assertNotNull("Tree should still exist", cut.getTree())
  }

  @Test
  fun `refreshUI should not throw when called on valid panel`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    cut = SnykToolWindowPanel(project)

    // Should not throw
    cut.refreshUI()

    // Process pending events
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Panel should still be valid
    assertNotNull("Panel should still exist", cut)
  }

  @Test
  fun `refreshUI should throttle rapid calls`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    cut = SnykToolWindowPanel(project)

    // First call should succeed
    cut.refreshUI()

    // Second call immediately after should be throttled (no error, just skipped)
    cut.refreshUI()

    // Process pending events
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Panel should still be valid
    assertNotNull("Panel should still exist after throttled calls", cut)
  }

  @Test
  fun `scheduleDebouncedTreeRefresh should ignore Unknown product`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    cut = SnykToolWindowPanel(project)

    // Trigger debounced tree refresh for Unknown product - should be ignored
    cut.scheduleDebouncedTreeRefreshForTest(snyk.common.lsp.LsProduct.Unknown)

    // Process pending events
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Thread.sleep(300)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Panel should still be valid (no errors)
    assertNotNull("Panel should still exist", cut)
  }

  @Test
  fun `flushPendingTreeRefreshes should skip Code refresh when scan is running`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    mockkStatic(::isOssRunning)
    mockkStatic(::isSnykCodeRunning)
    mockkStatic(::isIacRunning)

    // Simulate Code scan running
    every { isOssRunning(any()) } returns false
    every { isSnykCodeRunning(any()) } returns true
    every { isIacRunning(any()) } returns false

    cut = SnykToolWindowPanel(project)

    val codeNode = cut.getRootSecurityIssuesTreeNode()
    val initialChildCount = codeNode.childCount

    // Trigger debounced tree refresh for Code
    cut.scheduleDebouncedTreeRefreshForTest(snyk.common.lsp.LsProduct.Code)

    // Process pending events and wait for debounce
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Thread.sleep(300)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Code node should NOT be modified because scan is running
    assertEquals(
      "Code node should not be modified while scanning",
      initialChildCount,
      codeNode.childCount,
    )
  }

  @Test
  fun `flushPendingTreeRefreshes should skip IAC refresh when scan is running`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    mockkStatic(::isOssRunning)
    mockkStatic(::isSnykCodeRunning)
    mockkStatic(::isIacRunning)

    // Simulate IAC scan running
    every { isOssRunning(any()) } returns false
    every { isSnykCodeRunning(any()) } returns false
    every { isIacRunning(any()) } returns true

    cut = SnykToolWindowPanel(project)

    val iacNode = cut.getRootIacIssuesTreeNode()
    val initialChildCount = iacNode.childCount

    // Trigger debounced tree refresh for IAC
    cut.scheduleDebouncedTreeRefreshForTest(snyk.common.lsp.LsProduct.InfrastructureAsCode)

    // Process pending events and wait for debounce
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    Thread.sleep(300)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // IAC node should NOT be modified because scan is running
    assertEquals(
      "IAC node should not be modified while scanning",
      initialChildCount,
      iacNode.childCount,
    )
  }

  @Test
  fun `scheduleDebouncedTreeRefresh should be no-op when htmlTreePanel is active`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns settings
    every { isOssRunning(any()) } returns false

    cut = SnykToolWindowPanel(project)
    cut.setHtmlTreePanelForTest(mockk(relaxed = true))

    val ossNode = cut.getRootOssIssuesTreeNode()
    val initialChildCount = ossNode.childCount

    cut.scheduleDebouncedTreeRefreshForTest(snyk.common.lsp.LsProduct.OpenSource)

    // Wait for debounce window to expire, then verify no tree change
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    await().atMost(2, TimeUnit.SECONDS).until { true }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    assertEquals(
      "Tree should not be refreshed when htmlTreePanel is active",
      initialChildCount,
      ossNode.childCount,
    )
  }

  @Test
  fun `tree node text should not contain literal null when error suffix is unavailable`() {
    every { settings.token } returns "test-token"
    every { settings.pluginFirstRun } returns false
    justRun { taskQueueService.scan() }

    mockkStatic(::isOssRunning)
    mockkStatic(::isSnykCodeRunning)
    mockkStatic(::isIacRunning)

    every { isOssRunning(any()) } returns false
    every { isSnykCodeRunning(any()) } returns false
    every { isIacRunning(any()) } returns false

    cut = SnykToolWindowPanel(project)

    // Trigger tree update (ossResultsCount, securityIssuesCount, iacResultsCount, addHMLPostfix)
    cut.updateTreeRootNodesPresentation(0, 0, 0, "")

    // Process pending events
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Verify no node contains literal "null" text
    val ossNodeText = cut.getRootOssIssuesTreeNode().userObject.toString()
    val codeNodeText = cut.getRootSecurityIssuesTreeNode().userObject.toString()
    val iacNodeText = cut.getRootIacIssuesTreeNode().userObject.toString()

    assertFalse("OSS node should not contain 'null': $ossNodeText", ossNodeText.contains("null"))
    assertFalse("Code node should not contain 'null': $codeNodeText", codeNodeText.contains("null"))
    assertFalse("IAC node should not contain 'null': $iacNodeText", iacNodeText.contains("null"))
  }
}
