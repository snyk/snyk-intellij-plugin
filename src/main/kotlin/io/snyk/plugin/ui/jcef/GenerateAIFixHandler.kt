package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.runInBackground
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.Fix
import snyk.common.lsp.LanguageServerWrapper

class GenerateAIFixHandler(private val project: Project) {

    fun generateAIFixCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val aiFixQuery = JBCefJSQuery.create(jbCefBrowser)

        aiFixQuery.addHandler { value ->
            runInBackground("Snyk: getting AI fix proposals...") {
                val responseDiff: List<Fix> =
                    LanguageServerWrapper.getInstance(project).sendCodeFixDiffsCommand(value)

                val script = """
                        window.receiveAIFixResponse(${Gson().toJson(responseDiff)});
                    """.trimIndent()

                jbCefBrowser.cefBrowser.executeJavaScript(script, jbCefBrowser.cefBrowser.url, 0)
                JBCefJSQuery.Response("success")
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
                    })();
                    """
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }
}
