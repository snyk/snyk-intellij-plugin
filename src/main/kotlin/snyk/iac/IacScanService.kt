package snyk.iac

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.services.CliAdapter
import snyk.common.RelativePathHelper
import snyk.common.SnykError

/**
 * Wrap work with Snyk CLI for IaC (`iac test` command).
 */
@Service(Service.Level.PROJECT)
class IacScanService(project: Project) : CliAdapter<IacIssuesForFile, IacResult>(project) {

    fun scan(): IacResult = execute(listOf("iac", "test"))

    override fun getProductResult(cliIssues: List<IacIssuesForFile>?, snykErrors: List<SnykError>): IacResult =
        IacResult(cliIssues, snykErrors)

    override fun sanitizeCliIssues(cliIssues: IacIssuesForFile): IacIssuesForFile {
        // .copy() will check nullability of fields
        // determine relative path for each issue at scan time

        val helper = RelativePathHelper()
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(cliIssues.targetFilePath)
        val relativePath = virtualFile?.let { helper.getRelativePath(virtualFile, project) }

        val sanitized = cliIssues.copy(
            virtualFile = virtualFile,
            project = project,
            relativePath = relativePath,
            infrastructureAsCodeIssues = cliIssues.infrastructureAsCodeIssues
                .map {
                    if (it.lineStartOffset > 0 || virtualFile == null || !virtualFile.isValid) {
                        return@map it.copy()
                    }
                    val lineStartOffset = determineLineStartOffset(it, virtualFile)
                    return@map it.copy(lineStartOffset = lineStartOffset)
                }
        )

        return sanitized
    }

    private fun determineLineStartOffset(it: IacIssue, virtualFile: VirtualFile): Int {
        var lineStartOffset = it.lineStartOffset
        ApplicationManager.getApplication().runReadAction {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                val candidate = it.lineNumber - 1 // to 1-based count used in the editor
                val lineNumber = if (0 <= candidate && candidate < document.lineCount) candidate else 0
                lineStartOffset = document.getLineStartOffset(lineNumber)
            }
        }
        return lineStartOffset
    }

    override fun getCliIIssuesClass(): Class<IacIssuesForFile> = IacIssuesForFile::class.java

    override fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"infrastructureAsCodeIssues\":")

    override fun buildExtraOptions(): List<String> = listOf("--json")
}
