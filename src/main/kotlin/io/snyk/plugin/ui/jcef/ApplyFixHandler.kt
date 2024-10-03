package io.snyk.plugin.ui.jcef

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.DiffPatcher
import io.snyk.plugin.runInBackground
import io.snyk.plugin.toVirtualFile
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.jetbrains.concurrency.runAsync
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.common.lsp.LanguageServerWrapper
import java.io.IOException

class ApplyFixHandler(private val project: Project) {

    val logger = Logger.getInstance(this::class.java).apply {
        // tie log level to language server log level
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        if (languageServerWrapper.logger.isDebugEnabled) this.setLevel(LogLevel.DEBUG)
        if (languageServerWrapper.logger.isTraceEnabled) this.setLevel(LogLevel.TRACE)
    }

    fun generateApplyFixCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val applyFixQuery = JBCefJSQuery.create(jbCefBrowser)

        applyFixQuery.addHandler { value ->
            val params = value.split("|@", limit = 3)
            val fixId = params[0]  // Path to the file that needs to be patched
            val filePath = params[1]  // Path to the file that needs to be patched
            val patch = params[2]      // The patch we received from LS

            // Avoid blocking the UI thread
            runInBackground("Snyk: applying fix...") {
                val result = try {
                    applyPatchAndSave(project, filePath, patch)
                } catch (e: IOException) { // Catch specific file-related exceptions
                    logger.error("Error applying patch to file: $filePath. e:$e")
                    Result.failure(e)
                } catch (e: Exception) {
                    logger.error("Unexpected error applying patch. e:$e")
                    Result.failure(e)
                }

                if (result.isSuccess) {
                    val script = """
                            window.receiveApplyFixResponse(true);
                        """.trimIndent()
                    jbCefBrowser.cefBrowser.executeJavaScript(script, jbCefBrowser.cefBrowser.url, 0)
                    LanguageServerWrapper.getInstance().submitAutofixFeedbackCommand(fixId, "FIX_APPLIED")
                } else {
                    val errorMessage = "Error applying fix: ${result.exceptionOrNull()?.message}"
                    SnykBalloonNotificationHelper.showError(errorMessage, project)
                    val errorScript = """
                            window.receiveApplyFixResponse(false, "$errorMessage");
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

    private fun applyPatchAndSave(project: Project, filePath: String, patch: String): Result<Unit> {
        val virtualFile = filePath.toVirtualFile()
        val patcher = DiffPatcher()

        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                val originalContent = document.text
                val patchedContent = patcher.applyPatch(originalContent, patcher.parseDiff(patch))
                if (originalContent != patchedContent) {
                    document.setText(patchedContent)
                } else {
                    logger.warn("[applyPatchAndSave] Patch did not modify content: $filePath")
                }
            } else {
                logger.error("[applyPatchAndSave] Failed to find document for: $filePath")
                val errorMessage = "Failed to find document for: $filePath"
                SnykBalloonNotificationHelper.showError(errorMessage, project)
                return@runWriteCommandAction
            }
        }

        return Result.success(Unit)
    }
}
