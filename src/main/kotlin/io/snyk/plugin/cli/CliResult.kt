package io.snyk.plugin.cli

open class CliResult {
    lateinit var vulnerabilities: Array<Vulnerability>

    var uniqueCount: Int = 0
    lateinit var projectName: String
    var foundProjectCount: Int = 0
    lateinit var displayTargetFile: String
    lateinit var path: String

    var error: CliError? = null

    fun isSuccessful(): Boolean = error == null

    fun toCliGroupedResult(): CliGroupedResult {
        val vulnerabilitiesMap: MutableMap<String, MutableList<Vulnerability>> = mutableMapOf()
        var uniqueCount = 0
        var pathsCount = 0

        vulnerabilities.map {
            if (vulnerabilitiesMap.containsKey(it.id)) {
                val list = vulnerabilitiesMap[it.id]

                list!!.add(it)

                pathsCount++
            } else {
                vulnerabilitiesMap[it.id] = mutableListOf(it)

                pathsCount++
                uniqueCount++
            }
        }

        return CliGroupedResult(vulnerabilitiesMap, uniqueCount, pathsCount, projectName, displayTargetFile, path)
    }
}
