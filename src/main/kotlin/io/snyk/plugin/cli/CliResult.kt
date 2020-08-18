package io.snyk.plugin.cli

open class CliResult() {
    lateinit var vulnerabilities: Array<Vulnerability>

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

        return CliGroupedResult(vulnerabilitiesMap, uniqueCount, pathsCount)
    }
}
