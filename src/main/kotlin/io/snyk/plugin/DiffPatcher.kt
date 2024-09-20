package io.snyk.plugin

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

class DiffPatcher {

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
}
