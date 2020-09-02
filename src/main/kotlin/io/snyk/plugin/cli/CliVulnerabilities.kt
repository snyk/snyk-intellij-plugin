package io.snyk.plugin.cli

class CliVulnerabilities {
    lateinit var vulnerabilities: Array<Vulnerability>

    var uniqueCount: Int = 0
    lateinit var projectName: String
    var foundProjectCount: Int = 0
    lateinit var displayTargetFile: String
    lateinit var path: String

    fun toCliGroupedResult(): CliGroupedResult {
        val vulnerabilitiesMap: MutableMap<String, MutableList<Vulnerability>> = mutableMapOf()
        var uniqueCount = 0
        var pathsCount = 0

        vulnerabilities.map {
            val key = it.getPackageNameTitle()

            if (vulnerabilitiesMap.containsKey(key)) {
                val list = vulnerabilitiesMap[key]

                list!!.add(it)

                pathsCount++
            } else {
                vulnerabilitiesMap[key] = mutableListOf(it)

                pathsCount++
                uniqueCount++
            }
        }

        return CliGroupedResult(vulnerabilitiesMap, uniqueCount, pathsCount, projectName, displayTargetFile, path)
    }
}
