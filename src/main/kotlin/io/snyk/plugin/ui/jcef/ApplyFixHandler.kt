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

    fun generateApplyFixCommand(jbCefBrowser: JBCefBrowserBase): CefLoadHandlerAdapter {
        val applyFixQuery = JBCefJSQuery.create(jbCefBrowser)

        applyFixQuery.addHandler { value ->
            val params = value.split(":")
            val filePath = params[0]  // Path to the file that needs to be patched
            val patch = params[1]      // The patch we received from LS

            println("[applyFixHandler] Received request to apply fix on file: $filePath")
            println("[applyFixHandler] Patch to apply: $patch")


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

    private fun applyPatchAndSave(project: Project, filePath: String, patch: String): Boolean {
        val virtualFile = findVirtualFile(filePath) ?: run {
            println("[applyPatchAndSave] Virtual file not found for path: $filePath")
            return false
        }

        println("[applyPatchAndSave] Found virtual file: $virtualFile")

        val fileContent = ApplicationManager.getApplication().runReadAction<String?> {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            document?.text
        } ?: run {
            println("[applyPatchAndSave] Document not found or is null for virtual file: $filePath")
            return false
        }

        println("[applyPatchAndSave] Initial file content: $fileContent")

        val diffPatch = parseDiff(patch)
        val patchedContent = applyPatch(fileContent, diffPatch)

        println("[applyPatchAndSave] Patched content that will be written: $patchedContent")

        // Apply the patch inside a WriteCommandAction
        WriteCommandAction.runWriteCommandAction(project) {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            document?.let {
                it.setText(patchedContent)

                println("[applyPatchAndSave] Content after applying patch: ${it.text}")

                FileDocumentManager.getInstance().saveDocument(it)
                println("[applyPatchAndSave] Patch applied successfully!")
            } ?: run {
                println("[applyPatchAndSave] Failed to find document for saving patched content.")
            }
        }

        return true
    }

    fun applyPatch(fileContent: String, diffPatch: DiffPatch): String {
        val lines = fileContent.lines().toMutableList()

        for (hunk in diffPatch.hunks) {
            var originalLineIndex = hunk.startLineOriginal - 1  // Convert to 0-based index

            for (change in hunk.changes) {
                when (change) {
                    is Change.Addition -> {
                        lines.add(originalLineIndex, change.line)
                        originalLineIndex++
                    }
                    is Change.Deletion -> {
                        if (originalLineIndex < lines.size && lines[originalLineIndex].trim() == change.line) {
                            lines.removeAt(originalLineIndex)
                        }
                    }
                    is Change.Context -> {
                        originalLineIndex++  // Move past unchanged context lines
                    }
                }
            }
        }
        return lines.joinToString("\n")
    }

    fun parseDiff(diff: String): DiffPatch {
        val lines = diff.lines()
        val originalFile = lines.first { it.startsWith("---") }.substringAfter("--- ")
        val fixedFile = lines.first { it.startsWith("+++") }.substringAfter("+++ ")

        val hunks = mutableListOf<Hunk>()
        var currentHunk: Hunk? = null
        val changes = mutableListOf<Change>()

        for (line in lines) {
            when {
                line.startsWith("@@") -> {
                    // Parse hunk header (e.g., @@ -4,9 +4,14 @@)
                    val hunkHeader = line.substringAfter("@@ ").substringBefore(" @@").split(" ")
                    val original = hunkHeader[0].substring(1).split(",")
                    val fixed = hunkHeader[1].substring(1).split(",")

                    val startLineOriginal = original[0].toInt()
                    val numLinesOriginal = original.getOrNull(1)?.toInt() ?: 1
                    val startLineFixed = fixed[0].toInt()
                    val numLinesFixed = fixed.getOrNull(1)?.toInt() ?: 1

                    if (currentHunk != null) {
                        hunks.add(currentHunk.copy(changes = changes.toList()))
                        changes.clear()
                    }
                    currentHunk = Hunk(
                        startLineOriginal = startLineOriginal,
                        numLinesOriginal = numLinesOriginal,
                        startLineFixed = startLineFixed,
                        numLinesFixed = numLinesFixed,
                        changes = emptyList()
                    )
                }
                line.startsWith("---") || line.startsWith("+++") -> {
                    // Skip file metadata lines (--- and +++)
                    continue
                }
                line.startsWith("-") -> changes.add(Change.Deletion(line.substring(1).trim()))
                line.startsWith("+") -> changes.add(Change.Addition(line.substring(1).trim()))
                else -> changes.add(Change.Context(line.trim()))
            }
        }

        // Add the last hunk
        if (currentHunk != null) {
            hunks.add(currentHunk.copy(changes = changes.toList()))
        }

        return DiffPatch(
            originalFile = originalFile,
            fixedFile = fixedFile,
            hunks = hunks
        )
    }

    private fun findVirtualFile(filePath: String): VirtualFile? {
        return LocalFileSystem.getInstance().findFileByPath(filePath)
    }
}
