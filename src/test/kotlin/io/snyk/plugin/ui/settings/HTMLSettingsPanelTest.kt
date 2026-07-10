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

  // Data-loss guard: skip reload when user has unsaved edits
  fun `test shouldSkipReload returns true when isModified is true`() {
    val lsHtml = "<html><body>non-blank LS content</body></html>"

    val result = HTMLSettingsPanel.shouldSkipReload(lsHtml, isModified = true)

    assertTrue(
      "shouldSkipReload should return true (skip) when the panel has unsaved modifications",
      result,
    )
  }

  // Robustness guard: skip reload when LS returns an empty/blank string
  fun `test shouldSkipReload returns true when lsHtml is blank`() {
    val result = HTMLSettingsPanel.shouldSkipReload("   ", isModified = false)

    assertTrue("shouldSkipReload should return true (skip) when lsHtml is blank", result)
  }

  // Happy path: non-blank html, not modified → reload should proceed
  fun `test shouldSkipReload returns false when not modified and html not blank`() {
    val lsHtml = "<html><body>valid content</body></html>"

    val result = HTMLSettingsPanel.shouldSkipReload(lsHtml, isModified = false)

    assertFalse(
      "shouldSkipReload should return false (proceed) when html is non-blank and panel is unmodified",
      result,
    )
  }

  // Empty string is also blank
  fun `test shouldSkipReload returns true when lsHtml is empty string`() {
    val result = HTMLSettingsPanel.shouldSkipReload("", isModified = false)

    assertTrue("shouldSkipReload should return true (skip) when lsHtml is empty", result)
  }

  // Regression for the duplicate-timeout fix: while the LS never initializes, the panel must NOT
  // query it (which would force the blocking CLI protocol-version check) and must fall back.
  fun `test resolveHtmlContent never queries the LS and falls back when never initialized`() {
    var fetchCalls = 0
    val result =
      HTMLSettingsPanel.resolveHtmlContent(
        attempts = 3,
        isInitialized = { false },
        fetchConfigHtml = {
          fetchCalls++
          "<html>should never be returned</html>"
        },
        loadFallback = { "<html>fallback</html>" },
        onRetryWait = {}, // no real sleeping in the test
      )

    assertEquals("LS must never be queried while uninitialized", 0, fetchCalls)
    assertTrue("must fall back when the LS never comes up", result.usingFallback)
    assertEquals("<html>fallback</html>", result.html)
    assertNull("no LS error should be recorded when the LS was never queried", result.lastError)
  }

  // Happy path: LS initialized and returns non-blank HTML → return it, no fallback.
  fun `test resolveHtmlContent returns LS html without fallback when initialized`() {
    val result =
      HTMLSettingsPanel.resolveHtmlContent(
        attempts = 3,
        isInitialized = { true },
        fetchConfigHtml = { "<html>real config</html>" },
        loadFallback = { "<html>fallback</html>" },
        onRetryWait = {},
      )

    assertFalse("must not use fallback when the LS returned usable HTML", result.usingFallback)
    assertEquals("<html>real config</html>", result.html)
    assertNull(result.lastError)
  }

  // The poll picks up an init that completes on a later attempt (background init in progress).
  fun `test resolveHtmlContent polls until the LS becomes initialized`() {
    var initCalls = 0
    val result =
      HTMLSettingsPanel.resolveHtmlContent(
        attempts = 3,
        isInitialized = { initCalls++ >= 2 }, // false, false, then true on the 3rd check
        fetchConfigHtml = { "<html>late config</html>" },
        loadFallback = { "<html>fallback</html>" },
        onRetryWait = {},
      )

    assertFalse(result.usingFallback)
    assertEquals("<html>late config</html>", result.html)
  }

  // A blank LS response falls back rather than rendering an empty page.
  fun `test resolveHtmlContent falls back when the LS returns blank html`() {
    val result =
      HTMLSettingsPanel.resolveHtmlContent(
        attempts = 2,
        isInitialized = { true },
        fetchConfigHtml = { "   " },
        loadFallback = { "<html>fallback</html>" },
        onRetryWait = {},
      )

    assertTrue(result.usingFallback)
    assertEquals("<html>fallback</html>", result.html)
  }

  // An exception from the LS is captured as lastError and the panel falls back.
  fun `test resolveHtmlContent captures the LS error and falls back on exception`() {
    val result =
      HTMLSettingsPanel.resolveHtmlContent(
        attempts = 1,
        isInitialized = { true },
        fetchConfigHtml = { throw RuntimeException("boom") },
        loadFallback = { "<html>fallback</html>" },
        onRetryWait = {},
      )

    assertTrue(result.usingFallback)
    assertEquals("boom", result.lastError)
    assertEquals("<html>fallback</html>", result.html)
  }
}
