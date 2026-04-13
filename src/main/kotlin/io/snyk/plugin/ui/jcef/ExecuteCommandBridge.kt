package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.AuthenticationType
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
data class ExecuteCommandRequest(
  val command: String = "",
  val args: List<Any> = emptyList(),
  val callbackId: String? = null,
)

class ExecuteCommandBridge(private val project: Project) {
  private val log = logger<ExecuteCommandBridge>()
  private val gson = Gson()

  companion object {
    internal val SAFE_CALLBACK_ID = Regex("^[a-zA-Z0-9_]+$")
    // Login blocks until the user completes OAuth in the browser.
    private const val LOGIN_TIMEOUT_MS = 120_000L
    private val LONG_RUNNING_COMMANDS = setOf("snyk.login")
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
      val request = gson.fromJson(value, ExecuteCommandRequest::class.java)
      log.debug("ExecuteCommandBridge: received command=${request.command}, args=${request.args}")
      if (request.command.isBlank()) {
        log.warn("ExecuteCommandBridge: received empty command, ignoring")
        return
      }
      if (!request.command.startsWith("snyk.")) {
        log.warn(
          "ExecuteCommandBridge: webview attempted to execute disallowed command: ${request.command}"
        )
        return
      }
      if (request.callbackId != null && !SAFE_CALLBACK_ID.matches(request.callbackId)) {
        log.warn("ExecuteCommandBridge: invalid callbackId '${request.callbackId}', ignoring")
        return
      }
      val ls = LanguageServerWrapper.getInstance(project)
      runAsync {
        try {
          val timeout = if (request.command in LONG_RUNNING_COMMANDS) LOGIN_TIMEOUT_MS else 5_000L
          if (request.command == "snyk.login" && request.args.size >= 3) {
            saveLoginArgs(request.args)
          }
          val result = ls.executeCommandWithArgs(request.command, request.args, timeout)
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

  private fun saveLoginArgs(args: List<Any>) {
    try {
      val authMethodStr = args[0] as? String ?: return
      val endpoint = args[1] as? String ?: ""
      val ignoreUnknownCA = args[2] as? Boolean ?: false
      val authType =
        when (authMethodStr) {
          "oauth" -> AuthenticationType.OAUTH2
          "pat" -> AuthenticationType.PAT
          "token" -> AuthenticationType.API_TOKEN
          else -> AuthenticationType.OAUTH2
        }
      pluginSettings().authenticationType = authType
      pluginSettings().customEndpointUrl = endpoint
      pluginSettings().ignoreUnknownCA = ignoreUnknownCA
    } catch (e: Exception) {
      log.warn("ExecuteCommandBridge: error saving login args", e)
    }
  }
}
