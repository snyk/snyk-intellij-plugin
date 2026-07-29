package io.snyk.plugin.ui.jcef

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import snyk.common.SnykCachedResults
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.LsFolderSettingsKeys

class ToggleDeltaHandlerTest : BasePlatformTestCase() {
  private lateinit var settings: SnykApplicationSettingsStateService
  private lateinit var cut: ToggleDeltaHandler
  private lateinit var lsWrapperMock: LanguageServerWrapper
  private lateinit var realLsWrapper: LanguageServerWrapper
  private lateinit var cachedResults: SnykCachedResults

  override fun setUp() {
    super.setUp()
    unmockkAll()
    resetSettings(project)

    cachedResults = getSnykCachedResults(project)!!

    mockkStatic("io.snyk.plugin.UtilsKt")
    settings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns settings
    every { getSnykCachedResults(project) } returns cachedResults

    // Real wrapper, used only to read back getSettings() and assert on the actual wire contract
    // sent to snyk-ls, not on internal bookkeeping flags.
    realLsWrapper = LanguageServerWrapper(project)

    lsWrapperMock = mockk(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(project) } returns lsWrapperMock

    cut = ToggleDeltaHandler(project)
  }

  override fun tearDown() {
    unmockkAll()
    super.tearDown()
  }

  fun `test toggleDelta to total sends scan_net_new=false with changed=true on the wire`() {
    settings.setDeltaEnabled(true)

    cut.toggleDelta(false)

    val setting = realLsWrapper.getSettings().settings?.get(LsFolderSettingsKeys.SCAN_NET_NEW)
    assertEquals(false, setting?.value)
    assertEquals(true, setting?.changed)
  }

  fun `test toggleDelta to new sends scan_net_new=true with changed=true on the wire`() {
    cut.toggleDelta(true)

    val setting = realLsWrapper.getSettings().settings?.get(LsFolderSettingsKeys.SCAN_NET_NEW)
    assertEquals(true, setting?.value)
    assertEquals(true, setting?.changed)
  }

  fun `test toggleDelta clears cached scan results like the settings screen does`() {
    cachedResults.currentOSSResultsLS[mockk(relaxed = true)] = emptySet()
    cachedResults.currentSnykCodeResultsLS[mockk(relaxed = true)] = emptySet()
    cachedResults.currentIacResultsLS[mockk(relaxed = true)] = emptySet()

    cut.toggleDelta(false)

    assertTrue(cachedResults.currentOSSResultsLS.isEmpty())
    assertTrue(cachedResults.currentSnykCodeResultsLS.isEmpty())
    assertTrue(cachedResults.currentIacResultsLS.isEmpty())
  }

  fun `test toggleDelta updates language server configuration`() {
    cut.toggleDelta(true)

    verify { lsWrapperMock.updateConfiguration() }
  }
}
