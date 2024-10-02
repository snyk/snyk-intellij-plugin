package io.snyk.plugin.ui.jcef

import com.intellij.openapi.editor.colors.ColorKey
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
    }

    @Suppress("UNUSED_PARAMETER")
    fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val isDarkTheme = EditorColorsManager.getInstance().isDarkEditor
        val isHighContrast =
            EditorColorsManager.getInstance().globalScheme.name.contains("High contrast", ignoreCase = true)

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(
                browser: CefBrowser,
                frame: CefFrame,
                httpStatusCode: Int,
            ) {
                if (frame.isMain) {
                    val textColor = toCssHex(JBUI.CurrentTheme.Tree.FOREGROUND)
                    val linkColor = toCssHex(JBUI.CurrentTheme.Link.Foreground.ENABLED)

                    val borderColor = toCssHex(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
                    val labelColor = toCssHex(JBUI.CurrentTheme.Label.foreground())
                    val background = toCssHex(JBUI.CurrentTheme.Tree.BACKGROUND)
                    val issuePanelBackground = toCssHex(JBUI.CurrentTheme.DefaultTabs.background())
                    val tabUnderline = toCssHex(JBUI.CurrentTheme.DefaultTabs.underlineColor())
                    val redCodeBlock = toCssHex(JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR)
                    val greenCodeBlock = toCssHex(JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR)
                    val aiCodeBg = UIUtil.getTextFieldBackground()
                    val codeBlockText = toCssHex(JBUI.CurrentTheme.Tree.FOREGROUND)
                    val buttonColor = toCssHex(JBUI.CurrentTheme.Button.defaultButtonColorStart())

                    val globalScheme = EditorColorsManager.getInstance().globalScheme
                    val tearLineColor = globalScheme.getColor(ColorKey.find("TEARLINE_COLOR")) //TODO Replace with JBUI.CurrentTheme colors

                    val themeScript = """
                        (function(){
                        if (window.themeApplied) {
                            return;
                        }
                        window.themeApplied = true;
                        const style = getComputedStyle(document.documentElement);
                            const properties = {
                                '--text-color': "$codeBlockText",
                                '--link-color': "$linkColor",
                                '--data-flow-body-color': "$background",
                                '--example-line-added-color': "$greenCodeBlock",
                                '--example-line-removed-color': "$redCodeBlock",
                                '--tab-item-github-icon-color': "$textColor",
                                '--tab-item-hover-color': "$tabUnderline",
                                '--scrollbar-thumb-color': "${tearLineColor?.let { toCssHex(it) }}",
                                '--tabs-bottom-color': "$issuePanelBackground",
                                '--border-color': "$borderColor",
                                '--editor-color': "${toCssHex(aiCodeBg)}",
                                '--label-color': "'$labelColor'",
                                '--container-background-color': "${toCssHex(aiCodeBg)}",
                                '--generated-ai-fix-button-background-color': "$buttonColor",
                                '--dark-button-border-default': "$borderColor",
                                '--dark-button-default': "$buttonColor",
                            };
                            for (let [property, value] of Object.entries(properties)) {
                                document.documentElement.style.setProperty(property, value);
                            }

                            // Add theme class to body
                            const isDarkTheme = $isDarkTheme;
                            const isHighContrast = $isHighContrast;
                            document.body.classList.add(isHighContrast ? 'high-contrast' : (isDarkTheme ? 'dark' : 'light'));
                        })();
                        """
                    browser.executeJavaScript(themeScript, browser.url, 0)
                }
            }
        }
    }
}
