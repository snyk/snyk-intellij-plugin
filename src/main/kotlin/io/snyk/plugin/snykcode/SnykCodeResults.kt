package io.snyk.plugin.snykcode

import ai.deepcode.javaclient.core.SuggestionForFile
import io.snyk.plugin.Severity
import io.snyk.plugin.snykcode.core.SnykCodeFile

class SnykCodeResults(
    private val file2suggestions: Map<SnykCodeFile, List<SuggestionForFile>> = emptyMap()
) {
    fun suggestions(file: SnykCodeFile): List<SuggestionForFile> = file2suggestions[file] ?: emptyList()

    private val files: Set<SnykCodeFile> by lazy { file2suggestions.keys }

    val totalCount: Int by lazy { files.sumBy { getCount(it, -1) } }

    val totalErrorsCount: Int by lazy { files.sumBy { errorsCount(it) } }

    val totalWarnsCount: Int by lazy { files.sumBy { warnsCount(it) } }

    val totalInfosCount: Int by lazy { files.sumBy { infosCount(it) } }

    fun cloneFiltered(filter: (SuggestionForFile) -> Boolean): SnykCodeResults {
        return SnykCodeResults(
            file2suggestions
                .mapValues { (_, suggestions) -> suggestions.filter(filter) }
                .filterValues { it.isNotEmpty() }
        )
    }

    /** sort by Errors-Warnings-Infos */
    fun getSortedFiles(): Collection<SnykCodeFile> = files
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

    private fun errorsCount(file: SnykCodeFile) = getCount(file, 3)

    private fun warnsCount(file: SnykCodeFile) = getCount(file, 2)

    private fun infosCount(file: SnykCodeFile) = getCount(file, 1)

    /** @params severity - if `-1` then accept all  */
    private fun getCount(file: SnykCodeFile, severity: Int) =
        suggestions(file)
            .filter { severity == -1 || it.severity == severity }
            .sumBy { it.ranges.size }

    override fun equals(other: Any?): Boolean {
        return other is SnykCodeResults &&
            file2suggestions == other.file2suggestions
    }

    override fun hashCode(): Int {
        return file2suggestions.hashCode()
    }
}

fun SuggestionForFile.getSeverityAsEnum(): Severity = Severity.getFromIndex(this.severity)
