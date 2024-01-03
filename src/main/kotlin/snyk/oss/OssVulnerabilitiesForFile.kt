package snyk.oss

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import snyk.common.RelativePathHelper

data class OssVulnerabilitiesForFile(
    val vulnerabilities: List<Vulnerability>,
    private val displayTargetFile: String,
    val packageManager: String,
    val path: String,
    val remediation: Remediation? = null
) {
    val uniqueCount: Int get() = vulnerabilities.groupBy { it.id }.size

    val sanitizedTargetFile: String get() = displayTargetFile.replace("-lock", "")
    val virtualFile: VirtualFile
        get() = LocalFileSystem.getInstance().findFileByPath(this.path)!!

    var project: Project? = null

    // this is necessary as the creation of the class by the GSon is not initializing fields
    private var relativePathHelper: RelativePathHelper? = null
        get() = field ?: RelativePathHelper()

    var relativePath: String? = null
        get() = field ?: project?.let {
            field = relativePathHelper!!.getRelativePath(virtualFile, it)
            return field
        }

    fun toGroupedResult(): OssGroupedResult {
        val id2vulnerabilities = vulnerabilities.groupBy({ it.id }, { it })
        val uniqueCount = id2vulnerabilities.keys.size
        val pathsCount = id2vulnerabilities.values.flatten().size

        return OssGroupedResult(id2vulnerabilities, uniqueCount, pathsCount)
    }

    data class Upgrade(val upgradeTo: String)
    data class Remediation(val upgrade: Map<String, Upgrade>)
}
