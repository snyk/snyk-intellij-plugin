package io.snyk.plugin.ui.jcef

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.getDocument
import io.snyk.plugin.navigateToSource
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

// "|" works also for separating in windows paths
const val navigationSeparator = "|"

class OpenFileLoadHandlerGenerator(
    private val project: Project,
    private val virtualFiles: LinkedHashMap<String, VirtualFile?>,
) {
    fun openFile(value: String): JBCefJSQuery.Response {
        val values = value.replace("\n", "").split(navigationSeparator)
        val filePath = values[0]
        val startLine = values[1].toInt()
        val endLine = values[2].toInt()
        val startCharacter = values[3].toInt()
        val endCharacter = values[4].toInt()

        val virtualFile = virtualFiles[filePath] ?: return JBCefJSQuery.Response("success")
        val document = virtualFile.getDocument()?: return JBCefJSQuery.Response("success")

        // Ensure line numbers are within bounds
        val maxLine = document.lineCount - 1
        val safeStartLine = startLine.coerceIn(0, maxLine)
        val safeEndLine = endLine.coerceIn(0, maxLine)

        val startLineStartOffset = document.getLineStartOffset(safeStartLine)
        val startOffset = startLineStartOffset + startCharacter
        val endLineStartOffset = document.getLineStartOffset(safeEndLine)
        val endOffset = endLineStartOffset + endCharacter - 1

        navigateToSource(project, virtualFile, startOffset, endOffset)
        return JBCefJSQuery.Response("success")
    }

    fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val openFileQuery = JBCefJSQuery.create(jbCefBrowser)
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
                            window.openFileQuery(target.getAttribute("file-path") + "$navigationSeparator" +
                                 target.getAttribute("start-line") + "$navigationSeparator" +
                                 target.getAttribute("end-line") + "$navigationSeparator" +
                                 target.getAttribute("start-character") + "$navigationSeparator" +
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
