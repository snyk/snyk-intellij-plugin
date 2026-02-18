package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.jetbrains.concurrency.runAsync
import snyk.common.lsp.LanguageServerWrapper

data class TreeViewCommandRequest(
  val command: String = "",
  val args: List<Any> = emptyList(),
  val callbackId: String? = null,
)

class TreeViewBridgeHandler(private val project: Project) {
  private val logger = logger<TreeViewBridgeHandler>()
  private val gson = Gson()

  companion object {
    internal val ALLOWED_COMMANDS =
      setOf(
        "snyk.navigateToRange",
        "snyk.toggleTreeFilter",
        "snyk.getTreeViewIssueChunk",
        "snyk.setNodeExpanded",
        "snyk.showScanErrorDetails",
        "snyk.updateFolderConfig",
      )
    private val SAFE_CALLBACK_ID = Regex("^[a-zA-Z0-9_]+$")
  }

  fun generate(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
    val executeCommandQuery = JBCefJSQuery.create(jbCefBrowser)
    executeCommandQuery.addHandler { value -> handleCommand(value, jbCefBrowser) }

    return object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if (frame.isMain) {
          browser.executeJavaScript(buildBridgeScript(executeCommandQuery), browser.url, 0)
        }
      }
    }
  }

  internal fun handleCommand(value: String, jbCefBrowser: JBCefBrowserBase): JBCefJSQuery.Response {
    dispatchCommand(value) { callbackId, escaped ->
      jbCefBrowser.cefBrowser.executeJavaScript(
        "if(window.__ideCallbacks__&&window.__ideCallbacks__['$callbackId'])" +
          "{window.__ideCallbacks__['$callbackId']($escaped);}",
        jbCefBrowser.cefBrowser.url,
        0,
      )
    }
    return JBCefJSQuery.Response("ok")
  }

  internal fun dispatchCommand(
    value: String,
    callbackExecutor: ((String, String) -> Unit)? = null,
  ) {
    try {
      val request = gson.fromJson(value, TreeViewCommandRequest::class.java)
      logger.debug("TreeViewBridge: received command=${request.command}, args=${request.args}")
      if (request.callbackId != null && !SAFE_CALLBACK_ID.matches(request.callbackId)) {
        logger.warn("TreeViewBridge: invalid callbackId '${request.callbackId}', ignoring")
        return
      }
      if (request.command !in ALLOWED_COMMANDS) {
        logger.warn(
          "TreeViewBridge: command '${request.command}' is not in the allowlist, ignoring"
        )
        if (request.callbackId != null && callbackExecutor != null) {
          callbackExecutor(request.callbackId, gson.toJson(mapOf("error" to "command not allowed")))
        }
        return
      }
      val ls = LanguageServerWrapper.getInstance(project)
      runAsync {
        try {
          val result = ls.executeCommandWithArgs(request.command, request.args)
          if (request.callbackId != null && callbackExecutor != null) {
            val escaped = gson.toJson(result)
            callbackExecutor(request.callbackId, escaped)
          }
        } catch (e: Exception) {
          logger.warn("TreeViewBridge: error executing command ${request.command}", e)
          if (request.callbackId != null && callbackExecutor != null) {
            callbackExecutor(
              request.callbackId,
              gson.toJson(mapOf("error" to (e.message ?: "unknown error"))),
            )
          }
        }
      }
    } catch (e: Exception) {
      logger.warn("TreeViewBridge: error parsing command request", e)
    }
  }

  internal fun buildBridgeScript(executeCommandQuery: JBCefJSQuery): String =
    """
    (function() {
        if (window.__ideExecuteCommand__) return;
        window.__ideCallbacks__ = {};
        var __cbCounter = 0;
        window.__ideExecuteCommand__ = function(command, args, callback) {
            var callbackId = null;
            if (typeof callback === 'function') {
                callbackId = '__cb_' + (++__cbCounter);
                window.__ideCallbacks__[callbackId] = function(result) {
                    delete window.__ideCallbacks__[callbackId];
                    callback(result);
                };
            }
            var payload = JSON.stringify({
                command: command,
                args: args || [],
                callbackId: callbackId
            });
            ${executeCommandQuery.inject("payload")}
        };
    })();
    """
      .trimIndent()
}
