package io.snyk.plugin.ui.jcef

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.UIManager

/**
 * Centralized theme styling generator for JCEF HTML panels.
 * Replaces CSS variable placeholders with actual IDE theme values.
 */
class ThemeBasedStylingGenerator {
    companion object {
        /**
         * Replaces var(--xxx) or var(--xxx, fallback) with the replacement value.
         */
        private fun replaceVar(html: String, varName: String, replacement: String): String {
            // Match var(--varName) or var(--varName, anything)
            val pattern = Regex("""var\(--$varName(?:,[^)]*)??\)""")
            return html.replace(pattern, replacement)
        }

        /**
         * Lighten or darken a hex color.
         *
         * @param hex    "#RRGGBB"
         * @param factor -1.0 .. 1.0
         *               > 0 = lighten, < 0 = darken
         */
        fun adjustHexBrightness(hex: String, factor: Double): String {
            require(factor in -1.0..1.0) { "factor must be between -1.0 and 1.0" }

            val clean = hex.removePrefix("#")
            require(clean.length == 6) { "Expected 6-char hex like #RRGGBB" }

            fun adjust(comp: String): Int {
                val c = comp.toInt(16)
                val result = if (factor >= 0) {
                    // move toward 255
                    c + (255 - c) * factor
                } else {
                    // move toward 0
                    c + c * factor
                }
                return result.toInt().coerceIn(0, 255)
            }

            val r = adjust(clean.take(2))
            val g = adjust(clean.substring(2, 4))
            val b = adjust(clean.substring(4, 6))

            return "#%02X%02X%02X".format(r, g, b)
        }


        /**
         * Replaces var(--xxx) placeholders in HTML with actual theme values and adds theme classes to body.
         * Supports both VS Code-style (--vscode-xxx) and legacy (--text-color) variables.
         */
        fun replaceWithCustomStyles(htmlToReplace: String): String {
            var html = htmlToReplace
            val editorColorsManager = EditorColorsManager.getInstance()
            val isDarkTheme = editorColorsManager.isDarkEditor
            val darkenOrLightenFactor: Double = if (isDarkTheme) 1.0 else -1.0

            // All values computed fresh to support theme changes
            val bgColor = UIUtil.getPanelBackground().toHex()
            val fgColor = UIUtil.getLabelForeground().toHex()
            val dimmedFgColor = UIUtil.getLabelDisabledForeground().toHex()
            val borderColor = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground().toHex()
            val inputBgColor = UIUtil.getTextFieldBackground().toHex()
            val inputFgColor = UIUtil.getTextFieldForeground().toHex()
            val buttonBgColor = JBUI.CurrentTheme.Button.defaultButtonColorStart().toHex()
            val buttonFgColor = UIManager.getColor("Button.default.foreground")?.toHex()
                ?: UIManager.getColor("Button.foreground")?.toHex()
                ?: "#ffffff"
            val linkColor = JBUI.CurrentTheme.Link.Foreground.ENABLED.toHex()
            val fontFamily = JBUI.Fonts.label().asPlain().family
            val fontSize = JBFont.regular().size
            val scrollbarColor = UIManager.getColor("ScrollBar.thumbColor")?.toHex() ?: "#424242"
            val focusBorderColor = UIManager.getColor("Component.focusColor")?.toHex() ?: "#007acc"
            val infoBgColor = JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toHex()
            val infoBorderColor = JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR.toHex()
            val sectionBackground =
                adjustHexBrightness(UIUtil.getListBackground().toHex(), 0.05 * darkenOrLightenFactor)
            val editorUiTheme = editorColorsManager.schemeForCurrentUITheme
            val editorBackground = editorUiTheme.getColor(EditorColors.GUTTER_BACKGROUND)?.toHex()
                ?: editorUiTheme.defaultBackground.toHex()
            val globalScheme = editorColorsManager.globalScheme
            val tearLineColor = globalScheme.getColor(ColorKey.find("TEARLINE_COLOR"))?.toHex() ?: scrollbarColor
            val isHighContrast = globalScheme.name.contains("High contrast", ignoreCase = true)
            val successColor = JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR.toHex()
            val errorColor = JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR.toHex()
            val warningColor = JBUI.CurrentTheme.IconBadge.WARNING.toHex()
            val treeBackground = JBUI.CurrentTheme.Tree.BACKGROUND.toHex()
            val treeForeground = JBUI.CurrentTheme.Tree.FOREGROUND.toHex()
            val tabUnderline = JBUI.CurrentTheme.DefaultTabs.underlineColor().toHex()
            val tabBackground = JBUI.CurrentTheme.DefaultTabs.background().toHex()
            val labelColor = JBUI.CurrentTheme.Label.foreground().toHex()
            val labelBgColor = UIUtil.getLabelBackground().toHex()

            // VS Code style variables (settings panel)
            html = replaceVar(html, "vscode-font-family", "'$fontFamily', system-ui, -apple-system, sans-serif")
            html = replaceVar(html, "vscode-font-size", "${fontSize}px")
            html = replaceVar(html, "vscode-editor-background", bgColor)
            html = replaceVar(html, "vscode-foreground", fgColor)
            html = replaceVar(html, "vscode-editor-foreground", fgColor)
            html = replaceVar(html, "vscode-input-background", inputBgColor)
            html = replaceVar(html, "vscode-input-foreground", inputFgColor)
            html = replaceVar(html, "vscode-input-border", borderColor)
            html = replaceVar(html, "vscode-button-background", buttonBgColor)
            html = replaceVar(html, "vscode-button-foreground", buttonFgColor)
            html = replaceVar(html, "vscode-focusBorder", focusBorderColor)
            val checkboxBgColor = UIManager.getColor("CheckBox.background")?.toHex() ?: inputBgColor
            val checkboxFgColor = UIManager.getColor("CheckBox.foreground")?.toHex() ?: fgColor
            val checkboxSelectColor = UIManager.getColor("CheckBox.select")?.toHex() ?: buttonBgColor
            html = replaceVar(html, "vscode-checkbox-background", checkboxBgColor)
            html = replaceVar(html, "vscode-checkbox-foreground", checkboxFgColor)
            html = replaceVar(html, "vscode-checkbox-selectBackground", checkboxSelectColor)
            html = replaceVar(html, "vscode-checkbox-border", borderColor)
            html = replaceVar(html, "vscode-scrollbarSlider-background", scrollbarColor)
            html = replaceVar(html, "vscode-scrollbarSlider-hoverBackground", scrollbarColor)
            html = replaceVar(html, "vscode-scrollbarSlider-activeBackground", scrollbarColor)
            html = replaceVar(html, "vscode-panel-border", borderColor)
            html = replaceVar(html, "vscode-editor-inactiveSelectionBackground", sectionBackground)
            html = replaceVar(html, "vscode-inputValidation-infoBackground", infoBgColor)
            html = replaceVar(html, "vscode-inputValidation-infoForeground", fgColor)
            html = replaceVar(html, "vscode-inputValidation-infoBorder", infoBorderColor)
            html = replaceVar(html, "vscode-textBlockQuote-background", infoBgColor)
            html = replaceVar(html, "vscode-textBlockQuote-border", infoBorderColor)
            html = replaceVar(html, "vscode-descriptionForeground", dimmedFgColor)
            html = replaceVar(html, "vscode-inputValidation-errorBackground", errorColor)
            html = replaceVar(html, "vscode-inputValidation-errorBorder", errorColor)

            html = html.replace("--default-font: ", "--default-font: \"$fontFamily\", ")
            // Support var(--default-font) usage (fallback HTML uses this)
            html = replaceVar(html, "default-font", "'$fontFamily', system-ui, -apple-system, sans-serif")
            html = replaceVar(html, "main-font-size", getRelativeFontSize(fontSize))
            html = replaceVar(html, "text-color", fgColor)
            html = replaceVar(html, "dimmed-text-color", dimmedFgColor)
            html = replaceVar(html, "background-color", bgColor)
            html = replaceVar(html, "ide-background-color", bgColor)
            html = replaceVar(html, "border-color", borderColor)
            html = replaceVar(html, "horizontal-border-color", borderColor)
            html = replaceVar(html, "link-color", linkColor)
            html = replaceVar(html, "example-line-added-color", successColor)
            html = replaceVar(html, "example-line-removed-color", errorColor)
            html = replaceVar(html, "data-flow-body-color", treeBackground)
            html = replaceVar(html, "tab-item-github-icon-color", treeForeground)
            html = replaceVar(html, "scrollbar-thumb-color", tearLineColor)
            html = replaceVar(html, "tab-item-hover-color", tabUnderline)
            html = replaceVar(html, "tabs-bottom-color", tabBackground)
            html = replaceVar(html, "editor-color", inputBgColor)
            html = replaceVar(html, "label-color", labelColor)
            html = replaceVar(html, "container-background-color", editorBackground)
            html = replaceVar(html, "button-background-color", buttonBgColor)
            html = replaceVar(html, "button-text-color", fgColor)
            html = replaceVar(html, "input-border", borderColor)
            html = replaceVar(html, "disabled-background-color", borderColor)
            html = replaceVar(html, "warning-background", warningColor)
            html = replaceVar(html, "warning-text", labelBgColor)
            html = replaceVar(html, "code-background-color", editorBackground)
            html = replaceVar(html, "circle-color", borderColor)

            // Additional variables for fallback HTML compatibility
            html = replaceVar(html, "section-background-color", sectionBackground)
            html = replaceVar(html, "input-background-color", inputBgColor)
            html = replaceVar(html, "focus-color", focusBorderColor)

            // Theme classes on body
            val contrast = if (isHighContrast) "high-contrast" else ""
            val theme = if (isDarkTheme) "dark" else "light"
            val lineWithBody = html.lines().find { it.contains("<body") }
            if (lineWithBody != null) {
                val modifiedLineWithBody = if (lineWithBody.contains("class")) {
                    lineWithBody.replace("class", "class=\"$contrast $theme ")
                } else {
                    lineWithBody.replace("<body", "<body class=\"$contrast $theme \"")
                }
                html = html.replace(lineWithBody, modifiedLineWithBody)
            }
            return html
        }

        /**
         * Utility function to scale JBFonts appropriately for use in HTML elements.
         * JBFont defaults will be environment specific.
         * @see https://plugins.jetbrains.com/docs/intellij/typography.html#ide-font
         */
        internal fun getRelativeFontSize(inputFontSizePt: Int): String {
            val targetSizePx = 10
            val startingFontSizePt = if (inputFontSizePt > 0) inputFontSizePt else JBFont.regular().size
            val pxToPtMultiplier = 72.0 / 96.0
            val targetSizePt = targetSizePx * pxToPtMultiplier
            return String.format("%.3frem", targetSizePt / startingFontSizePt)
        }
    }
}
