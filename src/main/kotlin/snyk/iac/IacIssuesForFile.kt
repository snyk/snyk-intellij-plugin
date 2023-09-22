package snyk.iac

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

data class IacIssuesForFile(
    val infrastructureAsCodeIssues: List<IacIssue>,
    val targetFile: String,
    val targetFilePath: String,
    val packageManager: String
) {
    val obsolete: Boolean get() = infrastructureAsCodeIssues.any { it.obsolete }
    val ignored: Boolean get() = infrastructureAsCodeIssues.all { it.ignored }
    val uniqueCount: Int get() = infrastructureAsCodeIssues.groupBy { it.id }.size

    val virtualFile: VirtualFile? = LocalFileSystem.getInstance().findFileByPath(this.targetFilePath)
}

/* Real json Example: src/integTest/resources/iac-test-results/infrastructure-as-code-goof.json */
