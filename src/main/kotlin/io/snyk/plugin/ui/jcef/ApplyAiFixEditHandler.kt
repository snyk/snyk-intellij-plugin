package io.snyk.plugin.ui.jcef

import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.runInBackground
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper

class ApplyAiFixEditHandler(private val project: Project) {

    val logger = Logger.getInstance(this::class.java).apply {
        // tie log level to language server log level
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        if (languageServerWrapper.logger.isDebugEnabled) this.setLevel(LogLevel.DEBUG)
        if (languageServerWrapper.logger.isTraceEnabled) this.setLevel(LogLevel.TRACE)
    }

    fun generateApplyAiFixEditCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val applyFixQuery = JBCefJSQuery.create(jbCefBrowser)

        applyFixQuery.addHandler { value ->
            val params = value.split("|@", limit = 1)
            val fixId = params[0]  // ID of the AI fix for which we want to generate an edit command.
            logger.debug("Generate ApplAiFixEditCommand for fix $fixId")
            // Avoid blocking the UI thread
            runInBackground("Snyk: Send command to apply AI fix edit...") {
                LanguageServerWrapper.getInstance().sendCodeApplyAiFixEditCommand(fixId)
            }

            return@addHandler JBCefJSQuery.Response("success")
        }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    val script = """
                    (function() {
                        if (window.applyFixQuery) {
                            return;
                        }
                        window.applyFixQuery = function(value) { ${applyFixQuery.inject("value")} };
                    })();
                    """.trimIndent()
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }
}
