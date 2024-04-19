package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.getDocument
import io.snyk.plugin.navigateToSource
import io.snyk.plugin.snykcode.core.SnykCodeFile
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class OpenFileLoadHandlerGenerator(snykCodeFile: SnykCodeFile) {
    private val project = snykCodeFile.project
    private val virtualFile = snykCodeFile.virtualFile

    companion object {
        val darculaRegex = Regex(".*d(ar|ra)cula.*", RegexOption.IGNORE_CASE)
    }

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

    fun isDarcula(): Boolean {
        val scheme = EditorColorsManager.getInstance().globalScheme
        return darculaRegex.containsMatchIn(scheme.name)
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
                        // Attach a single event listener to the document
                        document.addEventListener('click', function(e) {
                            // Find the nearest ancestor
                            var target = e.target.closest('.data-flow-clickable-row');
                            if (target) {
                                e.preventDefault();
                                window.openFileQuery(target.getAttribute("file-path") + ":" +
                                                     target.getAttribute("start-line") + ":" +
                                                     target.getAttribute("end-line") + ":" +
                                                     target.getAttribute("start-character") + ":" +
                                                     target.getAttribute("end-character"));
                            }
                        });
                    })();
                """
                    browser.executeJavaScript(script, browser.url, 0)
                    val colorTheme = if (EditorColorsManager.getInstance().isDarkEditor) "dark" else "light"
                    val isDarculaTheme = isDarcula()
                    val themeScript = """
                        (function(){
                        if (window.themeApplied) {
                            return;
                        }
                        window.themeApplied = true;
                        const style = getComputedStyle(document.documentElement);
                        const properties = [
                          '--text-color',
                          '--background-color',
                          '--link-color',
                          '--info-text-color',
                          '--disabled-text-color',
                          '--selected-text-color',
                          '--error-text-color',
                          '--data-flow-file-color',
                          '--data-flow-body-color',
                          '--example-line-added-color',
                          '--example-line-removed-color',
                          '--tabs-bottom-color',
                          '--tab-item-color',
                          '--tab-item-hover-color',
                          '--tab-item-icon-color',
                          '--scrollbar-thumb-color',
                          '--scrollbar-color',
                        ]
                        properties.forEach(p => document.documentElement.style.setProperty(p, style.getPropertyValue(p + "-" + "${colorTheme}")))

                        if (${isDarculaTheme}) {
                            document.documentElement.style.setProperty('--data-flow-body-color', style.getPropertyValue('--data-flow-body-color-darcula'));
                            document.documentElement.style.setProperty('--example-line-added-color', style.getPropertyValue('--example-line-added-color-darcula'));
                            document.documentElement.style.setProperty('--example-line-removed-color', style.getPropertyValue('--example-line-removed-color-darcula'));
                        }
                        })();
                        """
                    browser.executeJavaScript(themeScript, browser.url, 0)
                }
            }
        }
    }
}

