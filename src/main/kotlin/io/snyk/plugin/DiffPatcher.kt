package io.snyk.plugin

import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.Patch

data class DiffPatch(
    val originalFile: String,
    val fixedFile: String,
    val patch: Patch<String>
)

class DiffPatcher {

    fun applyPatch(fileContent: String, diffPatch: DiffPatch): String {
        val lines = fileContent.lines()
        // java-diff-utils applyTo throws PatchFailedException which is checked. 
        // In Kotlin we don't need to declare it, but we should be aware.
        val patchedLines = diffPatch.patch.applyTo(lines)
        return patchedLines.joinToString("\n")
    }

    fun parseDiff(diff: String): DiffPatch {
        val lines = diff.lines()
        val originalFileLine = lines.firstOrNull { it.startsWith("---") } ?: ""
        val fixedFileLine = lines.firstOrNull { it.startsWith("+++") } ?: ""
        
        val originalFile = extractFileName(originalFileLine, "--- ")
        val fixedFile = extractFileName(fixedFileLine, "+++ ")

        val patch = UnifiedDiffUtils.parseUnifiedDiff(lines)

        return DiffPatch(
            originalFile = originalFile,
            fixedFile = fixedFile,
            patch = patch
        )
    }

    private fun extractFileName(line: String, prefix: String): String {
        if (!line.startsWith(prefix)) return ""
        
        var fileName = line.substringAfter(prefix)

        // Remove timestamp (git often adds a tab and timestamp)
        val tabIndex = fileName.indexOf('\t')
        if (tabIndex != -1) {
            fileName = fileName.substring(0, tabIndex)
        }

        // Handle quoted filenames
        if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
            fileName = fileName.substring(1, fileName.length - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        return fileName.trim()
    }
}
