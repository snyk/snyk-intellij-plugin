package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.getDocument
import io.snyk.plugin.navigateToSource
import io.snyk.plugin.SnykFile
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Color

class OpenFileLoadHandlerGenerator(snykFile: SnykFile) {
    private val project = snykFile.project
    private val virtualFile = snykFile.virtualFile

    fun openFile(value: String): JBCefJSQuery.Response {
        val values = value.replace("\n", "").split(":")
        val startLine = values[1].toInt()
        val endLine = values[2].toInt()
        val startCharacter = values[3].toInt()
        val endCharacter = values[4].toInt()

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

    fun shift(colorComponent: Int, d: Double): Int {
        val n = (colorComponent * d).toInt()
        return n.coerceIn(0, 255)
    }

    fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val openFileQuery = JBCefJSQuery.create(jbCefBrowser)
        openFileQuery.addHandler { openFile(it) }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
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

                        // Attach a single event listener to the document
                        document.addEventListener('click', function(e) {
                            // Find the nearest ancestor
                            var target = e.target.closest('.data-flow-clickable-row');
                            if (target) {
                                navigateToIssue(e, target);
                            }
                        });
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
                    val addedColor = Color(
                        shift(baseColor.red, 0.75),
                        baseColor.green,
                        shift(baseColor.blue, 0.75)
                    )
                    val removedColor = Color(
                        shift(baseColor.red, 1.25),
                        shift(baseColor.green, 0.85),
                        shift(baseColor.blue, 0.85)
                    )

                    val textColor = toCssHex(JBColor.namedColor("Label.foreground", JBColor.BLACK))
                    val linkColor = toCssHex(JBColor.namedColor("Link.activeForeground", JBColor.BLUE))
                    val dataFlowColor = toCssHex(baseColor)

                    val globalScheme = EditorColorsManager.getInstance().globalScheme
                    val tearLineColor = globalScheme.getColor(ColorKey.find("TEARLINE_COLOR")) // The closest color to target_rgb = (198, 198, 200)
                    val tabItemHoverColor = globalScheme.getColor(ColorKey.find("INDENT_GUIDE")) // The closest color to target_rgb = RGB (235, 236, 240)

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
                            };
                            for (let [property, value] of Object.entries(properties)) {
                                document.documentElement.style.setProperty(property, value);
                            }
                        })();
                        """
                    browser.executeJavaScript(themeScript, browser.url, 0)
                }
            }
        }
    }
}

