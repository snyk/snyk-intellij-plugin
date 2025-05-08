package io.snyk.plugin.ui.jcef

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color

fun Color.toHex() = ThemeBasedStylingGenerator.toCssHex(this)

class ThemeBasedStylingGenerator {
    companion object {
        fun toCssHex(color: Color): String {
            return "#%02x%02x%02x".format(color.red, color.green, color.blue)
        }
        fun replaceWithCustomStyles(htmlToReplace: String): String {
            var html = htmlToReplace;
            val editorColorsManager = EditorColorsManager.getInstance()
            val editorUiTheme = editorColorsManager.schemeForCurrentUITheme
            val textColor = UIUtil.getLabelForeground().toHex()
            val borderColor = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground().toHex()
            val editorBackground =
                editorUiTheme.getColor(EditorColors.GUTTER_BACKGROUND)?.toHex() ?: editorUiTheme.defaultBackground.toHex()
            val globalScheme = EditorColorsManager.getInstance().globalScheme
            val tearLineColor = globalScheme.getColor(ColorKey.find("TEARLINE_COLOR")) //TODO Replace with JBUI.CurrentTheme colors
            val isDarkTheme = EditorColorsManager.getInstance().isDarkEditor
            val isHighContrast =
                EditorColorsManager.getInstance().globalScheme.name.contains("High contrast", ignoreCase = true)
            html = html.replace("--default-font: ", "--default-font: \"${JBUI.Fonts.label().asPlain().family}\", ")
            html = html.replace("var(--main-font-size)", getRelativeFontSize(JBFont.regular().size))
            html = html.replace("var(--text-color)", textColor)
            html = html.replace("var(--dimmed-text-color)", UIUtil.getLabelDisabledForeground().toHex())
            html = html.replace("var(--background-color)", UIUtil.getPanelBackground().toHex())
            html = html.replace("var(--ide-background-color)", UIUtil.getPanelBackground().toHex())
            html = html.replace("var(--border-color)", borderColor)
            html = html.replace("var(--horizontal-border-color)", borderColor)
            html = html.replace("var(--link-color)", JBUI.CurrentTheme.Link.Foreground.ENABLED.toHex())
            html = html.replace("var(--example-line-added-color)", toCssHex(JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR))
            html = html.replace("var(--example-line-removed-color)", toCssHex(JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR))
            html = html.replace("var(--link-color)",toCssHex(JBUI.CurrentTheme.Link.Foreground.ENABLED))
            html = html.replace("var(--data-flow-body-color)", toCssHex(JBUI.CurrentTheme.Tree.BACKGROUND))
            html = html.replace("var(--tab-item-github-icon-color)",toCssHex(JBUI.CurrentTheme.Tree.FOREGROUND))
            html = html.replace("var(--scrollbar-thumb-color)", "${tearLineColor?.let { toCssHex(it)}}")
            html = html.replace("var(--tab-item-github-icon-color)", toCssHex(JBUI.CurrentTheme.Tree.FOREGROUND))
            html = html.replace("var(--tab-item-hover-color)", toCssHex(JBUI.CurrentTheme.DefaultTabs.underlineColor()))
            html = html.replace("var(--tabs-bottom-color)", toCssHex(JBUI.CurrentTheme.DefaultTabs.background()))
            html = html.replace("var(--border-color)", borderColor)
            html = html.replace("var(--editor-color)", toCssHex(UIUtil.getTextFieldBackground()))
            html = html.replace("var(--label-color)", toCssHex(JBUI.CurrentTheme.Label.foreground()))
            html = html.replace("var(--container-background-color)", toCssHex(UIUtil.getTextFieldBackground()))
            html = html.replace("var(--button-background-color)", toCssHex(JBUI.CurrentTheme.Button.defaultButtonColorStart()))
            html = html.replace("var(--button-text-color)", textColor /* TODO - Pick a better colour */)
            html = html.replace("var(--input-border)", borderColor)
            html = html.replace("var(--disabled-background-color)", borderColor)
            html = html.replace("var(--warning-background)", toCssHex(JBUI.CurrentTheme.IconBadge.WARNING))
            html = html.replace("var(--warning-text)", UIUtil.getLabelBackground().toHex())
            html = html.replace("var(--code-background-color)", editorBackground)
            html = html.replace("var(--container-background-color)", editorBackground)
            html = html.replace("var(--editor-color)", editorBackground)
            html = html.replace("var(--circle-color)", borderColor)
            val contrast = if (isHighContrast) "high-contrast" else ""
            val theme = if (isDarkTheme) "dark" else "light"
            val lineWithBody = html.lines().find { it.contains("<body") }
            if(lineWithBody != null) {
                    val modifiedLineWithBody =  if(lineWithBody.contains("class")) lineWithBody.replace("class", "class=\"$contrast $theme ") else lineWithBody.replace("<body", "<body class=\"$contrast $theme \"")
                    html = html.replace(lineWithBody, modifiedLineWithBody);
            }
            return html;
        }

        // Utility function to scale JBFonts appropriately for use in HTML elements that have been designed with px
        // values in mind. JBFont defaults will be environment specific - see
        // https://plugins.jetbrains.com/docs/intellij/typography.html#ide-font
        internal fun getRelativeFontSize(inputFontSizePt: Int): String {
            // Target size is the base size for which the HTML element was designed.
            val targetSizePx = 10
            val startingFontSizePt = if (inputFontSizePt > 0) inputFontSizePt else JBFont.regular().size

            // JBFont uses pt sizes, not px, so we convert here, using standard web values from
            // https://www.w3.org/TR/css3-values/#absolute-lengths
            val pxToPtMultiplier = 72.0 / 96.0
            val targetSizePt = targetSizePx * pxToPtMultiplier

            // CSS allows 3 decimal places of precision for calculations.
            return String.format("%.3frem", targetSizePt / startingFontSizePt)
        }
    }
}
