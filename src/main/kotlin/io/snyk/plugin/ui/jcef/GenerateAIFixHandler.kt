package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper

class GenerateAIFixHandler(private val project: Project) {

    fun generateAIFixCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val aiFixQuery = JBCefJSQuery.create(jbCefBrowser)

        aiFixQuery.addHandler { value ->
            val params = value.split(":")
            val folderURI = params[0]
            val fileURI = params[1]
            val issueID = params[2]

            // Avoids blocking the UI thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val responseDiff: List<LanguageServerWrapper.Fix> =
                        LanguageServerWrapper.getInstance().sendCodeFixDiffsCommand(folderURI, fileURI, issueID)

                    val script = """
                        window.receiveAIFixResponse(${Gson().toJson(responseDiff)});
                    """.trimIndent()

                    withContext(Dispatchers.Main) {
                        jbCefBrowser.cefBrowser.executeJavaScript(script, jbCefBrowser.cefBrowser.url, 0)
                        JBCefJSQuery.Response("success")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            return@addHandler JBCefJSQuery.Response("success")
        }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    val script = """
                    (function() {
                        if (window.aiFixQuery) {
                            return;
                        }
                        window.aiFixQuery = function(value) { ${aiFixQuery.inject("value")} };

                        const aiFixButton = document.getElementById('generate-ai-fix');
                        const issueId = aiFixButton.getAttribute('issue-id');
                        const folderPath = aiFixButton.getAttribute('folder-path');
                        const filePath = aiFixButton.getAttribute('file-path');

                        aiFixButton.addEventListener('click', () => {
                            console.log('Clicked AI Fix button. Path: ' + folderPath + ':' + filePath + ':' + issueId)
                            window.aiFixQuery(folderPath + ":" + filePath + ":" + issueId);
                        });
                    })();
                    """
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }
}
