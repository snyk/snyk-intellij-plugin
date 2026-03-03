package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.jetbrains.concurrency.runAsync
import snyk.common.lsp.LanguageServerWrapper

/**
 * Shared bridge for the `window.__ideExecuteCommand__` JS↔IDE contract. Used by both the HTML tree
 * view and the HTML settings page.
 *
 * Responsibilities:
 * - Inject the JS-side `window.__ideExecuteCommand__` bridge into a JCEF panel.
 * - Dispatch incoming command requests to the Language Server.
 * - Return results to the JS callback via `window.__ideCallbacks__`.
 */
class ExecuteCommandBridge(private val project: Project) {
  private val log = logger<ExecuteCommandBridge>()
  private val gson = Gson()

  companion object {
    internal val SAFE_CALLBACK_ID = Regex("^[a-zA-Z0-9_]+$")
  }

  /**
   * Generates the JS that defines `window.__ideExecuteCommand__` in the webview. Must be called
   * after each page load via `onLoadEnd`.
   */
  fun buildBridgeScript(executeCommandQuery: JBCefJSQuery): String =
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

  /**
   * Handles an incoming `JBCefJSQuery` value: dispatches the command and fires the JS callback.
   * Intended for use as a `JBCefJSQuery.addHandler` body.
   */
  fun handleCommand(value: String, jbCefBrowser: JBCefBrowserBase): JBCefJSQuery.Response {
    dispatch(value) { callbackId, escaped ->
      jbCefBrowser.cefBrowser.executeJavaScript(
        "if(window.__ideCallbacks__&&window.__ideCallbacks__['$callbackId'])" +
          "{window.__ideCallbacks__['$callbackId']($escaped);}",
        jbCefBrowser.cefBrowser.url,
        0,
      )
    }
    return JBCefJSQuery.Response("ok")
  }

  /**
   * Parses a JSON command request, sends it to the Language Server, and invokes [callbackExecutor]
   * with the result when finished.
   */
  internal fun dispatch(value: String, callbackExecutor: ((String, String) -> Unit)? = null) {
    try {
      val request = gson.fromJson(value, TreeViewCommandRequest::class.java)
      log.debug("ExecuteCommandBridge: received command=${request.command}, args=${request.args}")
      if (request.command.isBlank()) {
        log.warn("ExecuteCommandBridge: received empty command, ignoring")
        return
      }
      if (request.callbackId != null && !SAFE_CALLBACK_ID.matches(request.callbackId)) {
        log.warn("ExecuteCommandBridge: invalid callbackId '${request.callbackId}', ignoring")
        return
      }
      val ls = LanguageServerWrapper.getInstance(project)
      runAsync {
        try {
          val result = ls.executeCommandWithArgs(request.command, request.args)
          if (request.callbackId != null && callbackExecutor != null) {
            callbackExecutor(request.callbackId, gson.toJson(result))
          }
        } catch (e: Exception) {
          log.warn("ExecuteCommandBridge: error executing command ${request.command}", e)
          if (request.callbackId != null && callbackExecutor != null) {
            callbackExecutor(
              request.callbackId,
              gson.toJson(mapOf("error" to (e.message ?: "unknown error"))),
            )
          }
        }
      }
    } catch (e: Exception) {
      log.warn("ExecuteCommandBridge: error parsing command request", e)
    }
  }
}
