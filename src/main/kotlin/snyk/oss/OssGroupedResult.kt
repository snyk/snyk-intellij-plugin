package snyk.oss

data class OssGroupedResult(
    val id2vulnerabilities: Map<String, List<Vulnerability>>,
    val uniqueCount: Int,
    val pathsCount: Int
)
