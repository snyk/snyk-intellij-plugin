package io.snyk.plugin.ui.jcef

import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.runInBackground
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import snyk.common.IgnoreService
import snyk.common.lsp.LanguageServerWrapper
import java.io.File
import java.io.IOException

class IgnoreInFileHandler(
    private val project: Project,
    ) {
    val logger = Logger.getInstance(this::class.java).apply {
        // tie log level to language server log level
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        if (languageServerWrapper.logger.isDebugEnabled) this.setLevel(LogLevel.DEBUG)
        if (languageServerWrapper.logger.isTraceEnabled) this.setLevel(LogLevel.TRACE)
    }

    fun generateIgnoreInFileCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val applyIgnoreInFileQuery = JBCefJSQuery.create(jbCefBrowser)

        applyIgnoreInFileQuery.addHandler { value ->
            val params = value.split("|@", limit = 2)
            val issueId = params[0] // ID of issue that needs to be ignored
            val filePath = params[1]
            // Computed path that will be used in the snyk ignore command for the --path arg
            val computedPath = filePath.removePrefix("${project.getContentRootPaths().firstOrNull()}${File.separator}")
            // Avoid blocking the UI thread
            runInBackground("Snyk: applying ignore...") {
                val result = try {
                    applyIgnoreInFileAndSave(issueId, computedPath)
                } catch (e: IOException) {
                    logger.error("Error ignoring in file: $filePath. e:$e")
                    Result.failure(e)
                } catch (e: Exception) {
                    logger.error("Unexpected error applying ignore. e:$e")
                    Result.failure(e)
                }

                if (result.isSuccess) {
                    val script = """
                            window.receiveIgnoreInFileResponse(true);
                        """.trimIndent()
                    jbCefBrowser.cefBrowser.executeJavaScript(script, jbCefBrowser.cefBrowser.url, 0)
                } else {
                    val errorMessage = "Error ignoring in file: ${result.exceptionOrNull()?.message}"
                    SnykBalloonNotificationHelper.showError(errorMessage, project)
                    val errorScript = """
                            window.receiveIgnoreInFileResponse(false, "$errorMessage");
                        """.trimIndent()
                    jbCefBrowser.cefBrowser.executeJavaScript(errorScript, jbCefBrowser.cefBrowser.url, 0)
                }
            }

            return@addHandler JBCefJSQuery.Response("success")
        }

        return object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    val script = """
                    (function() {
                        if (window.applyIgnoreInFileQuery) {
                            return;
                        }
                        window.applyIgnoreInFileQuery = function(value) { ${applyIgnoreInFileQuery.inject("value")} };
                    })();
                    """.trimIndent()
                    browser.executeJavaScript(script, browser.url, 0)
                }
            }
        }
    }

    fun applyIgnoreInFileAndSave(issueId: String, filePath: String): Result<Unit> {
        val ignoreService = IgnoreService(project);
        if (issueId != "" && filePath != "") {
            ignoreService.ignoreInstance(issueId, filePath)
        } else {
            logger.error("[applyIgnoreInFileAndSave] Failed to find document for: $filePath")
            val errorMessage = "Failed to find document for: $filePath"
            SnykBalloonNotificationHelper.showError(errorMessage, project)
            return Result.failure(IOException(errorMessage))
        }
        return Result.success(Unit)
    }
}
