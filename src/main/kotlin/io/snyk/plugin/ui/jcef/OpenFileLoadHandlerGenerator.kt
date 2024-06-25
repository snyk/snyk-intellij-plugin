package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.getDocument
import io.snyk.plugin.navigateToSource
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Color

class OpenFileLoadHandlerGenerator(
    private val project: Project,
    private val virtualFiles: LinkedHashMap<String, VirtualFile?>,
) {
    fun openFile(value: String): JBCefJSQuery.Response {
        val values = value.replace("\n", "").split(":")
        val filePath = values[0]
        val startLine = values[1].toInt()
        val endLine = values[2].toInt()
        val startCharacter = values[3].toInt()
        val endCharacter = values[4].toInt()

        val virtualFile = virtualFiles[filePath] ?: return JBCefJSQuery.Response("success")

        ApplicationManager.getApplication().invokeLater {
            val document = virtualFile.getDocument()
            val startLineStartOffset = document?.getLineStartOffset(startLine) ?: 0
            val startOffset = startLineStartOffset + (startCharacter)
            val endLineStartOffset = document?.getLineStartOffset(endLine) ?: 0
            val endOffset = endLineStartOffset + endCharacter - 1

            navigateToSource(project, virtualFile, startOffset, endOffset)
        }

        return JBCefJSQuery.Response("success")
    }

    fun toCssHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }

    fun shift(
        colorComponent: Int,
        d: Double,
    ): Int {
        val n = (colorComponent * d).toInt()
        return n.coerceIn(0, 255)
    }

    fun getCodeDiffColors(
        baseColor: Color,
        isHighContrast: Boolean,
    ): Pair<Color, Color> {
        val addedColor =
            if (isHighContrast) {
                Color(28, 68, 40) // high contrast green
            } else {
                Color(shift(baseColor.red, 0.75), baseColor.green, shift(baseColor.blue, 0.75))
            }

        val removedColor =
            if (isHighContrast) {
                Color(84, 36, 38) // high contrast red
            } else {
                Color(shift(baseColor.red, 1.25), shift(baseColor.green, 0.85), shift(baseColor.blue, 0.85))
            }
        return Pair(addedColor, removedColor)
    }

    fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val openFileQuery = JBCefJSQuery.create(jbCefBrowser)
        val isDarkTheme = EditorColorsManager.getInstance().isDarkEditor
        val isHighContrast =
            EditorColorsManager.getInstance().globalScheme.name.contains("High contrast", ignoreCase = true)

        openFileQuery.addHandler { openFile(it) }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(
                browser: CefBrowser,
                frame: CefFrame,
                httpStatusCode: Int,
            ) {
                if (frame.isMain) {
                    val script = """
                    (function() {
                        if (window.openFileQuery) {
                            return;
                        }
                        window.openFileQuery = function(value) { ${openFileQuery.inject("value")} };

                        function navigateToIssue(e, target) {
                            e.preventDefault();
                            window.openFileQuery(target.getAttribute("file-path") + ":" +
                                 target.getAttribute("start-line") + ":" +
                                 target.getAttribute("end-line") + ":" +
                                 target.getAttribute("start-character") + ":" +
                                 target.getAttribute("end-character"));
                        }

                        const dataFlows = document.getElementsByClassName('data-flow-clickable-row');
                        for (let i = 0; i < dataFlows.length; i++) {
                            dataFlows[i].addEventListener('click', (e) => {
                                navigateToIssue(e, dataFlows[i]);
                            });
                        }
                        document.getElementById('position-line').addEventListener('click', (e) => {
                            // Find the first
                            var target = document.getElementsByClassName('data-flow-clickable-row')[0];
                            if (target) {
                                navigateToIssue(e, target);
                            }
                        });
                    })();
                """
                    browser.executeJavaScript(script, browser.url, 0)

                    val baseColor = UIUtil.getTextFieldBackground()
                    val (addedColor, removedColor) = getCodeDiffColors(baseColor, isHighContrast)

                    val textColor = toCssHex(JBUI.CurrentTheme.Label.foreground())
                    val linkColor = toCssHex(JBUI.CurrentTheme.Link.Foreground.ENABLED)
                    val dataFlowColor = toCssHex(baseColor)
                    val borderColor = toCssHex(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
                    val editorColor =
                        toCssHex(UIUtil.getTextFieldBackground())

                    val globalScheme = EditorColorsManager.getInstance().globalScheme
                    val tearLineColor =
                        globalScheme.getColor(ColorKey.find("TEARLINE_COLOR")) // The closest color to target_rgb = (198, 198, 200)
                    val tabItemHoverColor =
                        globalScheme.getColor(ColorKey.find("INDENT_GUIDE")) // The closest color to target_rgb = RGB (235, 236, 240)

                    val themeScript = """
                        (function(){
                        if (window.themeApplied) {
                            return;
                        }
                        window.themeApplied = true;
                        const style = getComputedStyle(document.documentElement);
                            const properties = {
                                '--text-color': "$textColor",
                                '--link-color': "$linkColor",
                                '--data-flow-body-color': "$dataFlowColor",
                                '--example-line-added-color': "${toCssHex(addedColor)}",
                                '--example-line-removed-color': "${toCssHex(removedColor)}",
                                '--tab-item-github-icon-color': "$textColor",
                                '--tab-item-hover-color': "${tabItemHoverColor?.let { toCssHex(it) }}",
                                '--scrollbar-thumb-color': "${tearLineColor?.let { toCssHex(it) }}",
                                '--tabs-bottom-color': "${tearLineColor?.let { toCssHex(it) }}",
                                '--border-color': "$borderColor",
                                '--editor-color': "$editorColor",
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
