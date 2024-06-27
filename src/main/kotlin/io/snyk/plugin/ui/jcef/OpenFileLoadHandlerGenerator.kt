package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.getDocument
import io.snyk.plugin.navigateToSource
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

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
                }
            }
        }
    }
}
