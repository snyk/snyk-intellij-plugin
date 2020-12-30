package io.snyk.plugin.snykcode

import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.psi.PsiFile

class SnykCodeResults(
    private val file2suggestions: Map<PsiFile, List<SuggestionForFile>> = emptyMap()
) {
    fun suggestions(file: PsiFile): List<SuggestionForFile> = file2suggestions[file] ?: emptyList()

    val files: Set<PsiFile>
        get() = file2suggestions.keys

    val totalCount: Int
        get() = file2suggestions.values.flatten().sumBy { it.ranges.size }

    fun cloneFiltered(filter: (SuggestionForFile) -> Boolean): SnykCodeResults {
        return SnykCodeResults(
            file2suggestions
                .mapValues { (_, suggestions) -> suggestions.filter(filter) }
                .filterValues { it.isNotEmpty() }
        )
    }
}

val SuggestionForFile.severityAsString: String
    get() = when (this.severity) {
        3 -> "high"
        2 -> "medium"
        1 -> "low"
        else -> "low"
    }

