package io.snyk.plugin.ui.jcef

import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.runInBackground
import io.snyk.plugin.settings.handleDeltaFindingsChange
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.LsFolderSettingsKeys

class ToggleDeltaHandler(val project: Project) {
  fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
    val toggleDeltaQuery = JBCefJSQuery.create(jbCefBrowser)
    toggleDeltaQuery.addHandler { deltaEnabled ->
      runInBackground("Snyk: updating configuration") { toggleDelta(deltaEnabled.toBoolean()) }
      return@addHandler JBCefJSQuery.Response("success")
    }

    return object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if (frame.isMain) {
          val script =
            """
                    (function() {
                        if (window.toggleDeltaQuery) {
                            return;
                        }
                        window.toggleDeltaQuery = function(isEnabled) { ${toggleDeltaQuery.inject("isEnabled")} };
                    })()
                    """
              .trimIndent()
          browser.executeJavaScript(script, browser.url, 0)
        }
      }
    }
  }

  internal fun toggleDelta(deltaEnabled: Boolean) {
    pluginSettings().setDeltaEnabled(deltaEnabled)
    pluginSettings().markExplicitlyChanged(LsFolderSettingsKeys.SCAN_NET_NEW)
    handleDeltaFindingsChange(project)
    LanguageServerWrapper.getInstance(project).updateConfiguration()
  }
}
