package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import io.snyk.plugin.DiffPatcher
import io.snyk.plugin.toVirtualFile
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.jetbrains.concurrency.runAsync
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.io.IOException


data class DiffPatch(
    val originalFile: String,
    val fixedFile: String,
    val hunks: List<Hunk>
)

data class Hunk(
    val startLineOriginal: Int,
    val numLinesOriginal: Int,
    val startLineFixed: Int,
    val numLinesFixed: Int,
    val changes: List<Change>
)

sealed class Change {
    data class Addition(val line: String) : Change()
    data class Deletion(val line: String) : Change()
    data class Context(val line: String) : Change()  // Unchanged line for context
}

class ApplyFixHandler(private val project: Project) {

    private val enableDebug = Logger.getInstance("Snyk Language Server").isDebugEnabled
    private val enableTrace = Logger.getInstance("Snyk Language Server").isTraceEnabled
    private val logger = Logger.getInstance(this::class.java)


    fun generateApplyFixCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val applyFixQuery = JBCefJSQuery.create(jbCefBrowser)

        applyFixQuery.addHandler { value ->
            val params = value.split("|@", limit = 2)
            val filePath = params[0]  // Path to the file that needs to be patched
            val patch = params[1]      // The patch we received from LS

            // Avoid blocking the UI thread
            runAsync {
                //var success = true
                val result = try {
                    applyPatchAndSave(project, filePath, patch)
                } catch (e: IOException) { // Catch specific file-related exceptions
                    log("Error applying patch to file: $filePath. e:$e")
                    Result.failure(e)
                } catch (e: Exception) {
                    log("Unexpected error applying patch. e:$e")
                    Result.failure(e)
                }

                ApplicationManager.getApplication().invokeLater {
                    if (result.isSuccess) {
                        val script = """
                            window.receiveApplyFixResponse(true);
                        """.trimIndent()
                        jbCefBrowser.cefBrowser.executeJavaScript(script, jbCefBrowser.cefBrowser.url, 0)
                    } else {
                        val errorMessage = "Error applying fix: ${result.exceptionOrNull()?.message}"
                        SnykBalloonNotificationHelper.showError(errorMessage, project)
                        val errorScript = """
                            window.receiveApplyFixResponse(false, "$errorMessage");
                        """.trimIndent()
                        jbCefBrowser.cefBrowser.executeJavaScript(errorScript, jbCefBrowser.cefBrowser.url, 0)
                    }
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

        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                if (document != null) {
                    val originalContent = document.text
                    val patchedContent = patcher.applyPatch(originalContent, patcher.parseDiff(patch))
                    if (originalContent != patchedContent) {
                        document.setText(patchedContent)
                    } else {
                        log("[applyPatchAndSave] Patch did not modify content: $filePath")
                    }
                } else {
                    log("[applyPatchAndSave] Failed to find document for: $filePath")
                    return@runWriteCommandAction
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            log("[applyPatchAndSave] Error applying patch to: $filePath. e: $e")
            Result.failure(e)
        }
    }

    private fun log(logMessage: String) {
        when {
            enableDebug -> logger.debug(logMessage)
            enableTrace -> logger.trace(logMessage)
            else -> logger.error(logMessage)
        }
    }
}