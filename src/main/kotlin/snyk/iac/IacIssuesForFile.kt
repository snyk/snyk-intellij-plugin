package snyk.iac

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import snyk.common.RelativePathHelper

data class IacIssuesForFile(
    val infrastructureAsCodeIssues: List<IacIssue>,
    val targetFile: String,
    val targetFilePath: String,
    val packageManager: String
) {
    val obsolete: Boolean get() = infrastructureAsCodeIssues.any { it.obsolete }
    val ignored: Boolean get() = infrastructureAsCodeIssues.all { it.ignored }
    val uniqueCount: Int get() = infrastructureAsCodeIssues.groupBy { it.id }.size

    val virtualFile: VirtualFile
        get() = LocalFileSystem.getInstance().findFileByPath(this.targetFilePath)!!

    var project: Project? = null

    // this is necessary as the creation of the class by the GSon is not initializing fields
    private var relativePathHelper: RelativePathHelper? = null
        get() = field ?: RelativePathHelper()

    var relativePath: String? = null
        get() = field ?: project?.let {
            field = relativePathHelper!!.getRelativePath(virtualFile, it)
            return field
        }
}
