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
}
