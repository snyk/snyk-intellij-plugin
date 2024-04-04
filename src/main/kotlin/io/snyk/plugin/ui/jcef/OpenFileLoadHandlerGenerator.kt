package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.getDocument
import io.snyk.plugin.navigateToSource
import io.snyk.plugin.snykcode.core.SnykCodeFile
import io.snyk.plugin.toVirtualFile
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class OpenFileLoadHandlerGenerator(snykCodeFile: SnykCodeFile) {
    private val project = snykCodeFile.project

    fun openFile(value: String): JBCefJSQuery.Response {
        var values = value.replace("\n", "").split(":")
        var filePath = values[0]
        var startLine = values[1].toInt()
        var endLine = values[2].toInt()
        var startCharacter = values[3].toInt()
        var endCharacter = values[4].toInt()

        ApplicationManager.getApplication().invokeLater {
            val virtualFile = filePath.toVirtualFile()
            val document = virtualFile.getDocument()
            val startLineStartOffset = document?.getLineStartOffset(startLine) ?: 0
            val startOffset = startLineStartOffset + (startCharacter)
            val endLineStartOffset = document?.getLineStartOffset(endLine) ?: 0
            val endOffset = endLineStartOffset + endCharacter - 1

            navigateToSource(project, virtualFile, startOffset, endOffset)
        }

        return JBCefJSQuery.Response("success")
    }

    fun generate(jbCefBrowser: JBCefBrowser): CefLoadHandlerAdapter {
        val openFileQuery = JBCefJSQuery.create(jbCefBrowser)
        openFileQuery.addHandler { openFile(it) }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    browser.executeJavaScript(
                        "window.openFileQuery = function(value) {" +
                            openFileQuery.inject("value") +
                            "};",
                        browser.url, 0
                    );

                    browser.executeJavaScript(
                        """
                    const dataFlowFilePaths = document.getElementsByClassName('data-flow-clickable-row')
                    for (let i = 0; i < dataFlowFilePaths.length; i++) {
                        const dataFlowFilePath = dataFlowFilePaths[i]
                        dataFlowFilePath.addEventListener('click', function (e) {
                          e.preventDefault()
                          window.openFileQuery(dataFlowFilePath.getAttribute("file-path")+":"+dataFlowFilePath.getAttribute("start-line")+":"+dataFlowFilePath.getAttribute("end-line")+":"+dataFlowFilePath.getAttribute("start-character")+":"+dataFlowFilePath.getAttribute("end-character"));
                        });
                    }""",
                        browser.url, 0
                    );
                }
            }
        }
    }
}

