package snyk.oss

class OssVulnerabilitiesForFile {
    lateinit var vulnerabilities: List<Vulnerability>
    lateinit var projectName: String
    lateinit var displayTargetFile: String
    lateinit var packageManager: String
    var remediation: Remediation? = null
    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size

    fun toGroupedResult(): OssGroupedResult {
        val id2vulnerabilities = vulnerabilities.groupBy({ it.id }, { it })
        val uniqueCount = id2vulnerabilities.keys.size
        val pathsCount = id2vulnerabilities.values.flatten().size

        return OssGroupedResult(id2vulnerabilities, uniqueCount, pathsCount)
    }

    data class Upgrade(val upgradeTo: String)
    data class Remediation(val upgrade: Map<String, Upgrade>)
}
