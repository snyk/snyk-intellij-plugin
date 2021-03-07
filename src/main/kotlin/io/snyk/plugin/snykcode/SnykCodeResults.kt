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

    /** sort by Errors-Warnings-Infos */
    fun getSortedFiles(): Collection<PsiFile> = files
        .sortedWith(Comparator { file1, file2 ->
            val file1Errors by lazy { errorsCount(file1) }
            val file2Errors by lazy { errorsCount(file2) }
            val file1Warns by lazy { warnsCount(file1) }
            val file2Warns by lazy { warnsCount(file2) }
            val file1Infos by lazy { infosCount(file1) }
            val file2Infos by lazy { infosCount(file2) }
            return@Comparator when {
                file1Errors != file2Errors -> file2Errors - file1Errors
                file1Warns != file2Warns -> file2Warns - file1Warns
                else -> file2Infos - file1Infos
            }
        })

    private fun errorsCount(file: PsiFile) = suggestions(file).filter { it.severity == 3 }.size
    private fun warnsCount(file: PsiFile) = suggestions(file).filter { it.severity == 2 }.size
    private fun infosCount(file: PsiFile) = suggestions(file).filter { it.severity == 1 }.size

    override fun equals(other: Any?): Boolean {
        return other is SnykCodeResults &&
            file2suggestions == other.file2suggestions
    }
}

val SuggestionForFile.severityAsString: String
    get() = when (this.severity) {
        3 -> "high"
        2 -> "medium"
        1 -> "low"
        else -> "low"
    }

