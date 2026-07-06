package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykTaskQueueService
import org.junit.Test
import snyk.UIComponentFinder

/**
 * Panel-state switching tests for SnykToolWindowPanel. The native Swing tree was removed; these
 * tests cover displayEmptyDescription() logic only, not tree layout.
 *
 * Note: SnykToolWindowPanelIntegTest (HeavyPlatformTestCase, @Ignore("Too unstable in CI")) was
 * not restored — it was already skipped in CI before deletion.
 */
class SnykToolWindowPanelTest : LightPlatform4TestCase() {
  private val settings = mockk<SnykApplicationSettingsStateService>(relaxed = true)
  private val taskQueueService = mockk<SnykTaskQueueService>(relaxed = true)

  override fun setUp() {
    super.setUp()
    unmockkAll()
    ApplicationManager.getApplication()
      .replaceService(SnykApplicationSettingsStateService::class.java, settings, testRootDisposable)
    project.replaceService(SnykTaskQueueService::class.java, taskQueueService, testRootDisposable)
    every { settings.pluginFirstRun } returns false
  }

  override fun tearDown() {
    unmockkAll()
    super.tearDown()
  }

  @Test
  fun `displays auth panel when token is null`() {
    every { settings.token } returns null
    val cut = SnykToolWindowPanel(project)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val authPanel = UIComponentFinder.getJPanelByName(cut, "authPanel")
    assertNotNull("authPanel should be visible when token is null", authPanel)
  }

  @Test
  fun `description panel is always present after construction`() {
    every { settings.token } returns null
    val cut = SnykToolWindowPanel(project)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    val descriptionPanel = UIComponentFinder.getJPanelByName(cut, "descriptionPanel")
    assertNotNull("descriptionPanel should always be present", descriptionPanel)
  }

  @Test
  fun `no auth panel when token is set and caches are empty`() {
    every { settings.token } returns "test-token"
    val cut = SnykToolWindowPanel(project)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    // token is set → auth panel must not be shown
    val authPanel = UIComponentFinder.getJPanelByName(cut, "authPanel")
    assertNull("authPanel must not be visible when token is set", authPanel)
  }

  @Test
  fun `no auth panel when token set and all caches have keys pointing to empty sets`() {
    // Regression: after a clean LS scan the cache maps have keys → empty sets.
    // noIssuesInAnyProductFound() used to return false (map.isEmpty() check), causing
    // the "Select an issue" message to appear instead of "Scan your project".
    every { settings.token } returns "test-token"
    val cut = SnykToolWindowPanel(project)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val cache = getSnykCachedResults(project)!!
    cache.currentOSSResultsLS[mockk(relaxed = true)] = emptySet()
    cache.currentSnykCodeResultsLS[mockk(relaxed = true)] = emptySet()
    cache.currentIacResultsLS[mockk(relaxed = true)] = emptySet()
    cache.currentSecretsResultsLS[mockk(relaxed = true)] = emptySet()

    cut.chooseMainPanelToDisplay()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Auth panel must not appear — token is set
    val authPanel = UIComponentFinder.getJPanelByName(cut, "authPanel")
    assertNull("authPanel must not appear when token is set", authPanel)
    // Description panel must still be present
    val descriptionPanel = UIComponentFinder.getJPanelByName(cut, "descriptionPanel")
    assertNotNull("descriptionPanel must be present", descriptionPanel)
  }
}
