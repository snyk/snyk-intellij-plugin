package io.snyk.plugin.ui.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class ApplyFixHandler(private val project: Project) {

    fun generateApplyFixCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val applyFixQuery = JBCefJSQuery.create(jbCefBrowser)

        applyFixQuery.addHandler { value ->
            val params = value.split(":")
            val filePath = params[0]  // Path to the file that needs to be patched
            val patch = params[1]      // The patch we received from LS

            // Avoid blocking the UI thread
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val success = applyPatchAndSave(project, filePath, patch)

                    val script = """
                        window.receiveApplyFixResponse($success);
                    """.trimIndent()

                    withContext(Dispatchers.Main) {
                        jbCefBrowser.cefBrowser.executeJavaScript(script, jbCefBrowser.cefBrowser.url, 0)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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

    // Applies a patch to the file content and saves it
    private fun applyPatchAndSave(project: Project, filePath: String, patch: String): Boolean {
        val virtualFile = findVirtualFile(filePath)

        if (virtualFile == null) {
            println("[applyPatchAndSave] Virtual file not found for path: $filePath")
            return false
        }

        // Use runReadAction to read the file content
        val fileContent = ApplicationManager.getApplication().runReadAction<String?> {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            document?.text
        }

        // If fileContent is null, log the error and return
        if (fileContent == null) {
            println("[applyPatchAndSave] Document not found or is null for virtual file: $filePath")
            return false
        }

        // Apply the patch to the content outside any read or write actions
        val patchedContent = applyPatch(fileContent, patch)

        // If the patch fails to apply, log and return
        if (patchedContent == null) {
            println("[applyPatchAndSave] Failed to apply patch.")
            return false
        }

        // Now apply the patch inside a WriteCommandAction
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                document.setText(patchedContent)
                FileDocumentManager.getInstance().saveDocument(document)
                println("[applyPatchAndSave] Patch applied successfully!")
            }
        }

        return true
    }

    private fun applyPatch(fileContent: String, patch: String): String? {
        println("[applyPatch] Applying patch: $patch")

        // Simple patch application (find & replace logic)
        val patchLines = patch.lines()
        val patchedContent = StringBuilder(fileContent)

        for (line in patchLines) {
            if (line.startsWith("-")) {
                // Remove the line that starts with '-'
                val oldLine = line.substring(1).trim()
                val startIndex = patchedContent.indexOf(oldLine)
                if (startIndex != -1) {
                    patchedContent.delete(startIndex, startIndex + oldLine.length)
                }
            } else if (line.startsWith("+")) {
                // Add the line that starts with '+'
                val newLine = line.substring(1).trim()
                patchedContent.append("\n").append(newLine)
            }
        }

        return patchedContent.toString()
    }

    private fun findVirtualFile(filePath: String): VirtualFile? {
        return LocalFileSystem.getInstance().findFileByPath(filePath)
    }
}
