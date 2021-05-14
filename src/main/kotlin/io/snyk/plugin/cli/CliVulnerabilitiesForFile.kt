package io.snyk.plugin.cli

class CliVulnerabilitiesForFile {
    lateinit var vulnerabilities: Array<Vulnerability>
    lateinit var projectName: String
    lateinit var displayTargetFile: String
    lateinit var packageManager: String

    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size

    fun toCliGroupedResult(): CliGroupedResult {
        val id2vulnerabilities = vulnerabilities.groupBy({ it.id }, { it })
        val uniqueCount = id2vulnerabilities.keys.size
        val pathsCount = id2vulnerabilities.values.flatten().size

        return CliGroupedResult(id2vulnerabilities, uniqueCount, pathsCount)
    }
}
