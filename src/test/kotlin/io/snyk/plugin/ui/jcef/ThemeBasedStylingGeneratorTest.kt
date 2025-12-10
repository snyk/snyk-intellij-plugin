package io.snyk.plugin.ui.jcef

import com.intellij.testFramework.fixtures.BasePlatformTestCase


class ThemeBasedStylingGeneratorTest: BasePlatformTestCase() {

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
        val html = """<style>body { font-family: var(--vscode-font-family, 'Segoe UI', sans-serif); }</style>"""
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
        val html = """<div style="color: var(--text-color); background: var(--background-color);">test</div>"""
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
        val html = """
            <style>
                body {
                    color: var(--vscode-foreground);
                    background: var(--vscode-editor-background);
                    font-family: var(--vscode-font-family);
                }
            </style>
        """.trimIndent()
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
}
