package io.snyk.plugin.ui.jcef

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Color

fun Color.toHex() = ThemeBasedStylingGenerator.toCssHex(this)

class ThemeBasedStylingGenerator {
    companion object {
        fun toCssHex(color: Color): String {
            return "#%02x%02x%02x".format(color.red, color.green, color.blue)
        }
        fun replaceWithCustomStyles(htmlToReplace: String):String {
            var html = htmlToReplace;
            val editorColorsManager = EditorColorsManager.getInstance()
            val editorUiTheme = editorColorsManager.schemeForCurrentUITheme
            val borderColor = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground().toHex()
            val editorBackground =
                editorUiTheme.getColor(EditorColors.GUTTER_BACKGROUND)?.toHex() ?: editorUiTheme.defaultBackground.toHex()
            val globalScheme = EditorColorsManager.getInstance().globalScheme
            val tearLineColor = globalScheme.getColor(ColorKey.find("TEARLINE_COLOR")) //TODO Replace with JBUI.CurrentTheme colors
            val isDarkTheme = EditorColorsManager.getInstance().isDarkEditor
            val isHighContrast =
                EditorColorsManager.getInstance().globalScheme.name.contains("High contrast", ignoreCase = true)
            html = html.replace("--default-font: ", "--default-font: \"${JBUI.Fonts.label().asPlain().family}\", ")
            html = html.replace("var(--text-color)", UIUtil.getLabelForeground().toHex())
            html = html.replace("var(--background-color)", UIUtil.getPanelBackground().toHex())
            html = html.replace("var(--ide-background-color)", UIUtil.getPanelBackground().toHex())
            html = html.replace("var(--border-color)", borderColor)
            html = html.replace("var(--horizontal-border-color)", borderColor)
            html = html.replace("var(--link-color)", JBUI.CurrentTheme.Link.Foreground.ENABLED.toHex())
            html = html.replace("var(--example-line-added-color)", toCssHex(JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR))
            html = html.replace("var(--example-line-removed-color)", toCssHex(JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR))
            html = html.replace("var(--text-color)", toCssHex(JBUI.CurrentTheme.Tree.FOREGROUND))
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
            html = html.replace("var(--generated-ai-fix-button-background-color)", toCssHex(JBUI.CurrentTheme.Button.defaultButtonColorStart()))
            html = html.replace("var(--dark-button-border-default)", borderColor)
            html = html.replace("var(--dark-button-default)", toCssHex(JBUI.CurrentTheme.Button.defaultButtonColorStart()))
            html = html.replace("var(--disabled-background-color)", borderColor)
            html = html.replace(
                "var(--code-background-color)",
                editorBackground
            )
            html = html.replace(
                "var(--container-background-color)",
                editorBackground
            )

            html = html.replace(
                "var(--editor-color)",
                editorBackground
            )
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
    }
}
