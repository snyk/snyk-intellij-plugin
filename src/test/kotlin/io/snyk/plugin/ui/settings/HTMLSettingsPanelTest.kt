package io.snyk.plugin.ui.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.snyk.plugin.ui.jcef.ThemeBasedStylingGenerator

/**
 * Unit tests for HTMLSettingsPanel logic that can run without a live JCEF browser.
 *
 * Note: constructing HTMLSettingsPanel directly requires a real JCEF browser (JBCefApp) which is
 * not available in headless CI. UNIT-002 and UNIT-003 are tested via the companion object helper
 * [HTMLSettingsPanel.themedHtmlDiffers], which exposes the same pure comparison logic that the
 * instance method delegates to.
 *
 * UNIT-001 (the isDisposed guard at the pooled-thread entry inside reloadFromLanguageServer) is
 * deferred due to the JCEF headless constraint. INT-003 in SnykLanguageClientTest tests that a
 * disposed SnykLanguageClient does not call reloadFromLanguageServer() at all; it does NOT cover
 * the guard inside HTMLSettingsPanel.reloadFromLanguageServer() itself. That guard is verified by
 * code review only.
 */
class HTMLSettingsPanelTest : BasePlatformTestCase() {

  // UNIT-002: when themed HTML is unchanged the helper returns false (no re-render needed)
  fun `test themedHtmlDiffers returns false when themed HTML is unchanged`() {
    val rawHtml = "<html><body>hello</body></html>"
    // Compute what the theme engine produces for this raw HTML
    val themed = ThemeBasedStylingGenerator.replaceWithCustomStyles(rawHtml)

    // Passing the same themed value as currentThemedHtml → content is unchanged
    val result = HTMLSettingsPanel.themedHtmlDiffers(rawHtml, themed)

    assertFalse(
      "themedHtmlDiffers should return false when themed content matches currentThemedHtml",
      result,
    )
  }

  // UNIT-003: when themed HTML changes the helper returns true (re-render should proceed)
  fun `test themedHtmlDiffers returns true when themed HTML is different`() {
    val rawHtml = "<html><body>hello</body></html>"
    val differentCurrentHtml = "<html><body>completely different content</body></html>"

    val result = HTMLSettingsPanel.themedHtmlDiffers(rawHtml, differentCurrentHtml)

    assertTrue(
      "themedHtmlDiffers should return true when themed content differs from currentThemedHtml",
      result,
    )
  }

  // Additional: null currentThemedHtml (first load) → always differs → re-render proceeds
  fun `test themedHtmlDiffers returns true when currentThemedHtml is null`() {
    val rawHtml = "<html><body>hello</body></html>"

    val result = HTMLSettingsPanel.themedHtmlDiffers(rawHtml, null)

    assertTrue(
      "themedHtmlDiffers should return true when currentThemedHtml is null (first load)",
      result,
    )
  }
}
