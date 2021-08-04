package snyk.oss

class OssVulnerabilitiesForFile {
    lateinit var vulnerabilities: Array<Vulnerability>
    lateinit var projectName: String
    lateinit var displayTargetFile: String
    lateinit var packageManager: String

    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size

    fun toGroupedResult(): OssGroupedResult {
        val id2vulnerabilities = vulnerabilities.groupBy({ it.id }, { it })
        val uniqueCount = id2vulnerabilities.keys.size
        val pathsCount = id2vulnerabilities.values.flatten().size

        return OssGroupedResult(id2vulnerabilities, uniqueCount, pathsCount)
    }
}
