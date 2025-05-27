package io.snyk.plugin.ui.jcef

import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.runInBackground
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper


class SubmitIgnoreRequestHandler(val project: Project) {

    fun submitIgnoreRequestCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val submitIgnoreRequest = JBCefJSQuery.create(jbCefBrowser)

        submitIgnoreRequest.addHandler { value ->
            val params = value.split("@|@", limit = 4)
            val issueId = params[0]
            val ignoreType = params[1]
            val ignoreExpirationDate = params[2]
            val ignoreReason = params[3]

            runInBackground("Snyk: submitting ignore request...") {
                LanguageServerWrapper.getInstance(project)
                    .sendSubmitIgnoreRequestCommand("create", issueId, ignoreType, ignoreReason, ignoreExpirationDate)
                JBCefJSQuery.Response("success")
            }
            return@addHandler JBCefJSQuery.Response("success")
        }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    val script = """
                    (function() {
                        if (window.submitIgnoreRequest) {
                            return;
                        }
                        window.submitIgnoreRequest = function(value) { ${submitIgnoreRequest.inject("value")} };
                    })();
                    """
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }
}
