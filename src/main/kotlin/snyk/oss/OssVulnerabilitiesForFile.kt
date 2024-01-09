package snyk.oss

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

data class OssVulnerabilitiesForFile(
    val vulnerabilities: List<Vulnerability>,
    private val displayTargetFile: String,
    val packageManager: String,
    val path: String,
    val remediation: Remediation? = null,
    val virtualFile: VirtualFile? = null,
    val relativePath: String? = null,
    val project: Project? = null
) {
    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size
    val sanitizedTargetFile: String get() = displayTargetFile.replace("-lock", "")

    fun toGroupedResult(): OssGroupedResult {
        val id2vulnerabilities = vulnerabilities.groupBy({ it.id }, { it })
        val uniqueCount = id2vulnerabilities.keys.size
        val pathsCount = id2vulnerabilities.values.flatten().size

        return OssGroupedResult(id2vulnerabilities, uniqueCount, pathsCount)
    }

    data class Upgrade(val upgradeTo: String)
    data class Remediation(val upgrade: Map<String, Upgrade>)
}
