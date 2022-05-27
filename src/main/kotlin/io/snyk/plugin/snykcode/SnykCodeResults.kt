package io.snyk.plugin.snykcode

import ai.deepcode.javaclient.core.SuggestionForFile
import io.snyk.plugin.Severity
import io.snyk.plugin.snykcode.core.SnykCodeFile

class SnykCodeResults(
    private val file2suggestions: Map<SnykCodeFile, List<SuggestionForFile>> = emptyMap()
) {
    private fun suggestions(file: SnykCodeFile): List<SuggestionForFile> = file2suggestions[file] ?: emptyList()

    private val files: Set<SnykCodeFile> by lazy { file2suggestions.keys }

    val totalCount: Int by lazy { files.sumBy { getCount(it, null) } }

    val totalCriticalCount: Int by lazy { files.sumBy { criticalCount(it) } }

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

    // todo? also sort by line in file
    /** sort by Errors-Warnings-Infos */
    fun getSortedSuggestions(file: SnykCodeFile): List<SuggestionForFile> =
        suggestions(file).sortedByDescending { it.getSeverityAsEnum() }

    /** sort by Errors-Warnings-Infos */
    fun getSortedFiles(): Collection<SnykCodeFile> = files
        .sortedWith(Comparator { file1, file2 ->
            val file1Criticals by lazy { criticalCount(file1) }
            val file2Criticals by lazy { criticalCount(file2) }
            val file1Errors by lazy { errorsCount(file1) }
            val file2Errors by lazy { errorsCount(file2) }
            val file1Warns by lazy { warnsCount(file1) }
            val file2Warns by lazy { warnsCount(file2) }
            val file1Infos by lazy { infosCount(file1) }
            val file2Infos by lazy { infosCount(file2) }
            return@Comparator when {
                file1Criticals != file2Criticals -> file2Criticals - file1Criticals
                file1Errors != file2Errors -> file2Errors - file1Errors
                file1Warns != file2Warns -> file2Warns - file1Warns
                else -> file2Infos - file1Infos
            }
        })

    private fun criticalCount(file: SnykCodeFile) = getCount(file, Severity.CRITICAL)

    private fun errorsCount(file: SnykCodeFile) = getCount(file, Severity.HIGH)

    private fun warnsCount(file: SnykCodeFile) = getCount(file, Severity.MEDIUM)

    private fun infosCount(file: SnykCodeFile) = getCount(file, Severity.LOW)

    /** @params severity - if `NULL then accept all  */
    private fun getCount(file: SnykCodeFile, severity: Severity?) =
        suggestions(file)
            .filter { severity == null || it.getSeverityAsEnum() == severity }
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
