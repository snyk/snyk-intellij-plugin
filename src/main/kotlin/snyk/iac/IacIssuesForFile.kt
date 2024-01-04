package snyk.iac

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

data class IacIssuesForFile(
    val infrastructureAsCodeIssues: List<IacIssue>,
    val targetFile: String,
    val targetFilePath: String,
    val packageManager: String,
    val virtualFile: VirtualFile?,
    val project: Project?,
    val relativePath: String? = null,
) {
    val obsolete: Boolean get() = infrastructureAsCodeIssues.any { it.obsolete }
    val ignored: Boolean get() = infrastructureAsCodeIssues.all { it.ignored }
    val uniqueCount: Int get() = infrastructureAsCodeIssues.groupBy { it.id }.size
}
