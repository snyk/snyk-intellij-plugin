package io.snyk.plugin.ui.jcef

import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class TreeViewBridgeHandler(private val project: Project) {
  private val bridge = ExecuteCommandBridge(project)

  fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
    val executeCommandQuery = JBCefJSQuery.create(jbCefBrowser)
    executeCommandQuery.addHandler { value -> bridge.handleCommand(value, jbCefBrowser) }

    return object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if (frame.isMain) {
          browser.executeJavaScript(bridge.buildBridgeScript(executeCommandQuery), browser.url, 0)
        }
      }
    }
  }

  internal fun handleCommand(value: String, jbCefBrowser: JBCefBrowserBase): JBCefJSQuery.Response =
    bridge.handleCommand(value, jbCefBrowser)

  internal fun dispatchCommand(
    value: String,
    callbackExecutor: ((String, String) -> Unit)? = null,
  ) = bridge.dispatch(value, callbackExecutor)

  internal fun buildBridgeScript(executeCommandQuery: JBCefJSQuery): String =
    bridge.buildBridgeScript(executeCommandQuery)
}
