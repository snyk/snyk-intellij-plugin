package io.snyk.plugin.ui.jcef

import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.pluginSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper

class ToggleDeltaHandler {
    fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val toggleDeltaQuery = JBCefJSQuery.create(jbCefBrowser)
        toggleDeltaQuery.addHandler {deltaEnabled ->
            if (deltaEnabled.toBoolean()) {
                pluginSettings().setDeltaEnabled()
            } else {
                pluginSettings().setDeltaDisabled()
            }

            val languageServerWrapper = LanguageServerWrapper.getInstance()
            languageServerWrapper.updateConfiguration(runScan = false)
            return@addHandler JBCefJSQuery.Response("success")
        }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    val script = """
                    (function() {
                        if (window.toggleDeltaQuery) {
                            return;
                        }
                        window.toggleDeltaQuery = function(value) { ${toggleDeltaQuery.inject("value")} };

                        document.getElementById('totalIssues')?.addEventListener('click', () => {
                            window.toggleDeltaQuery(false);
                        });
                        document.getElementById('newIssues')?.addEventListener('click', () => {
                            window.toggleDeltaQuery(true);
                        });
                    })()
                    """.trimIndent()
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }
}
