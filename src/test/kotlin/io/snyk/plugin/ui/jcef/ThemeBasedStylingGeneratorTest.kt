package io.snyk.plugin.ui.jcef

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ThemeBasedStylingGeneratorTest : BasePlatformTestCase() {

  fun `test precise font scaling`() {
    assertEquals("0.577rem", ThemeBasedStylingGenerator.getRelativeFontSize(13)) // macOS
    assertEquals("0.625rem", ThemeBasedStylingGenerator.getRelativeFontSize(12)) // Windows
    assertEquals("0.500rem", ThemeBasedStylingGenerator.getRelativeFontSize(15)) // Linux
  }

  fun `test font scaling range`() {
    for (size in -100..100) {
      val scaling = ThemeBasedStylingGenerator.getRelativeFontSize(size)
      assertTrue(scaling.endsWith("rem"))
      val factor = scaling.replace("rem", "").toFloat()
      assertTrue(factor > 0)
    }
  }

  fun `test replaceWithCustomStyles replaces vscode variables without fallback`() {
    val html = """<div style="color: var(--vscode-foreground);">test</div>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Should replace with actual color value (hex format)
    assertFalse(result.contains("var(--vscode-foreground)"))
    assertTrue(result.contains("#"))
  }

  fun `test replaceWithCustomStyles replaces vscode variables with fallback`() {
    val html = """<div style="color: var(--vscode-foreground, #cccccc);">test</div>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Should replace entire var() including fallback with actual color
    assertFalse(result.contains("var(--vscode-foreground"))
    assertFalse(result.contains("#cccccc)"))
    assertTrue(result.contains("#"))
  }

  fun `test replaceWithCustomStyles replaces font family with fallbacks`() {
    val html =
      """<style>body { font-family: var(--vscode-font-family, 'Segoe UI', sans-serif); }</style>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Should replace with actual font family
    assertFalse(result.contains("var(--vscode-font-family"))
    assertTrue(result.contains("font-family:"))
  }

  fun `test replaceWithCustomStyles replaces font size`() {
    val html = """<style>body { font-size: var(--vscode-font-size, 13px); }</style>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Should replace with actual font size in px
    assertFalse(result.contains("var(--vscode-font-size"))
    assertTrue(result.contains("px"))
  }

  fun `test replaceWithCustomStyles replaces legacy variables`() {
    val html =
      """<div style="color: var(--text-color); background: var(--background-color);">test</div>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Should replace legacy variables
    assertFalse(result.contains("var(--text-color)"))
    assertFalse(result.contains("var(--background-color)"))
  }

  fun `test replaceWithCustomStyles adds theme class to body`() {
    val html = """<html><body>content</body></html>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Should add dark or light class to body
    assertTrue(result.contains("<body class=\"") || result.contains("class=\""))
    assertTrue(result.contains("dark") || result.contains("light"))
  }

  fun `test replaceWithCustomStyles adds theme class to body with existing class`() {
    val html = """<html><body class="existing">content</body></html>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Should prepend theme class to existing class
    assertTrue(result.contains("class=\""))
    assertTrue(result.contains("dark") || result.contains("light"))
  }

  fun `test replaceWithCustomStyles handles multiple variables`() {
    val html =
      """
            <style>
                body {
                    color: var(--vscode-foreground);
                    background: var(--vscode-editor-background);
                    font-family: var(--vscode-font-family);
                }
            </style>
        """
        .trimIndent()
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // All variables should be replaced
    assertFalse(result.contains("var(--vscode-foreground)"))
    assertFalse(result.contains("var(--vscode-editor-background)"))
    assertFalse(result.contains("var(--vscode-font-family)"))
  }

  fun `test replaceWithCustomStyles injects default font into css variable declaration`() {
    val html = """<style>:root { --default-font: sans-serif; }</style>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Should inject IDE font family before the existing value
    assertTrue(result.contains("--default-font:"))
    // The pattern is: --default-font: "fontFamily", sans-serif
    assertTrue(result.contains("\","))
  }

  fun `test replaceWithCustomStyles preserves non-variable content`() {
    val html = """<div class="test">Some content with no variables</div>"""
    val result = ThemeBasedStylingGenerator.replaceWithCustomStyles(html)

    // Content should be preserved (except body class addition if body present)
    assertTrue(result.contains("Some content with no variables"))
    assertTrue(result.contains("class=\"test\""))
  }

  // Direct tests for replaceAllCssVars

  fun `test replaceAllCssVars replaces var usage from map`() {
    val html = """color: var(--text-color);"""
    val varMap = mapOf("text-color" to "#ffffff")
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, varMap, emptyMap())

    assertEquals("color: #ffffff;", result)
  }

  fun `test replaceAllCssVars replaces var usage with fallback`() {
    val html = """color: var(--text-color, #000000);"""
    val varMap = mapOf("text-color" to "#ffffff")
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, varMap, emptyMap())

    // Should replace entire var() including fallback
    assertEquals("color: #ffffff;", result)
  }

  fun `test replaceAllCssVars preserves var with fallback when not in map`() {
    val html = """color: var(--unknown-var, #000000);"""
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, emptyMap(), emptyMap())

    // Should keep original var() with fallback
    assertEquals("""color: var(--unknown-var, #000000);""", result)
  }

  fun `test replaceAllCssVars preserves var without fallback when not in map`() {
    val html = """color: var(--unknown-var);"""
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, emptyMap(), emptyMap())

    // Should keep original var()
    assertEquals("""color: var(--unknown-var);""", result)
  }

  fun `test replaceAllCssVars replaces css declaration with prefix`() {
    val html = """--default-font: sans-serif;"""
    val declPrefixMap = mapOf("default-font" to "\"Arial\", ")
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, emptyMap(), declPrefixMap)

    assertEquals("""--default-font: "Arial", sans-serif;""", result)
  }

  fun `test replaceAllCssVars handles multiple variables in single pass`() {
    val html =
      """
            .test {
                color: var(--text-color);
                background: var(--bg-color);
                border: 1px solid var(--border-color, #ccc);
            }
        """
        .trimIndent()
    val varMap = mapOf("text-color" to "#111", "bg-color" to "#222", "border-color" to "#333")
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, varMap, emptyMap())

    assertTrue(result.contains("#111"))
    assertTrue(result.contains("#222"))
    assertTrue(result.contains("#333"))
    assertFalse(result.contains("var(--"))
  }

  fun `test replaceAllCssVars handles both var usage and declaration in same html`() {
    val html =
      """
            :root { --default-font: sans-serif; }
            body { font-family: var(--default-font); color: var(--text-color); }
        """
        .trimIndent()
    val varMap = mapOf("default-font" to "'Arial', sans-serif", "text-color" to "#000")
    val declPrefixMap = mapOf("default-font" to "\"Segoe UI\", ")
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, varMap, declPrefixMap)

    // Declaration should have prefix
    assertTrue(result.contains("--default-font: \"Segoe UI\", sans-serif"))
    // var() usages should be replaced
    assertTrue(result.contains("font-family: 'Arial', sans-serif"))
    assertTrue(result.contains("color: #000"))
  }

  fun `test replaceAllCssVars handles hyphenated variable names`() {
    val html = """color: var(--vscode-editor-foreground);"""
    val varMap = mapOf("vscode-editor-foreground" to "#abcdef")
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, varMap, emptyMap())

    assertEquals("color: #abcdef;", result)
  }

  fun `test replaceAllCssVars handles underscored variable names`() {
    val html = """color: var(--my_custom_var);"""
    val varMap = mapOf("my_custom_var" to "#123456")
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, varMap, emptyMap())

    assertEquals("color: #123456;", result)
  }

  fun `test replaceAllCssVars handles nested fallback syntax`() {
    val html = """color: var(--primary, var(--fallback, #000));"""
    val varMap = mapOf("primary" to "#fff")
    val result = ThemeBasedStylingGenerator.replaceAllCssVars(html, varMap, emptyMap())

    // Should replace outer var, inner var remains but parsing stops at first )
    assertTrue(result.contains("#fff"))
  }
}
